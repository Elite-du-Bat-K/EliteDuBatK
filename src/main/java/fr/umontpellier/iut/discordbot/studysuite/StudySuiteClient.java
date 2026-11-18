package fr.umontpellier.iut.discordbot.studysuite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import fr.umontpellier.iut.discordbot.config.ConfigStructure;
import fr.umontpellier.iut.discordbot.studysuite.model.Assignment;
import fr.umontpellier.iut.discordbot.studysuite.model.NewAssignment;
import fr.umontpellier.iut.discordbot.studysuite.model.RoleMapping;
import fr.umontpellier.iut.discordbot.studysuite.model.StudyEvent;
import fr.umontpellier.iut.discordbot.studysuite.model.StudyGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client de l'API StudySuite.
 * <p>
 * Le planning est public. Les routes du bot ({@code /api/bot/*}) demandent une des clés configurées côté API
 * ({@code bot.apiKeys}), envoyée en {@code Authorization: Bot <clé>}. Sans clé, le bot ne peut pas deviner la classe
 * d'un membre depuis ses rôles.
 * <p>
 * Les appels sont bloquants : à lancer hors du thread d'événements de JDA.
 */
public class StudySuiteClient {
    public static final String DEFAULT_BASE_URL = "https://study-info.umontp.fr";

    /** Les groupes changent à la main, depuis l'admin du site : quelques minutes de retard ne gênent personne. */
    private static final Duration GROUPS_TTL = Duration.ofMinutes(10);
    private static final Duration MAPPINGS_TTL = Duration.ofMinutes(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Logger logger = LoggerFactory.getLogger(StudySuiteClient.class);
    private final Gson gson = new Gson();
    private final HttpClient http;
    private final String baseUrl;
    @Nullable
    private final String apiKey;

    private final Expiring<List<StudyGroup>> groups = new Expiring<>(GROUPS_TTL);
    private final Map<String, Expiring<List<RoleMapping>>> mappings = new ConcurrentHashMap<>();

    public StudySuiteClient(@Nullable ConfigStructure.StudySuiteConfig config) {
        String configuredUrl = config == null ? null : config.getBaseUrl();
        this.baseUrl = stripTrailingSlash(configuredUrl == null || configuredUrl.isBlank() ? DEFAULT_BASE_URL : configuredUrl);
        String key = config == null ? null : config.getApiKey();
        this.apiKey = key == null || key.isBlank() ? null : key;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

        if (this.apiKey == null) {
            logger.warn("No StudySuite API key configured: members' classes cannot be read from their roles");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean hasApiKey() {
        return apiKey != null;
    }

    /** Tous les groupes, cachés compris : un groupe caché peut être le parent d'un groupe visible. */
    public List<StudyGroup> getGroups() {
        return groups.get(() -> getData("/api/groups?includeHidden=true", new TypeToken<List<StudyGroup>>() {}.getType()));
    }

    public GroupHierarchy getHierarchy() {
        return new GroupHierarchy(getGroups());
    }

    /**
     * Les horaires des cours sont demandés avec le décalage de Paris ({@code 2026-09-14T08:00:00.000+02:00}) : c'est
     * le seul format qui désigne le même instant avant et après le passage de l'API aux vrais instants (migration
     * 0017), alors que le format par défaut change de sens.
     */
    private static final String EVENT_DATE_FORMAT = "&dateFormat=iso-offset";

    /** Les cours d'un jour, tous groupes confondus. */
    public List<StudyEvent> getDayEvents(LocalDate date) {
        return getData("/api/events/day?date=" + date + EVENT_DATE_FORMAT, new TypeToken<List<StudyEvent>>() {}.getType());
    }

    /** Les cours de la semaine (du lundi au dimanche) contenant {@code date}, tous groupes confondus. */
    public List<StudyEvent> getWeekEvents(LocalDate date) {
        return getData("/api/events/week?date=" + date + EVENT_DATE_FORMAT, new TypeToken<List<StudyEvent>>() {}.getType());
    }

    /** Les rôles Discord de ce serveur associés à un groupe. Vide sans clé d'API. */
    public List<RoleMapping> getRoleMappings(String guildId) {
        if (apiKey == null) {
            return List.of();
        }
        return mappings.computeIfAbsent(guildId, id -> new Expiring<>(MAPPINGS_TTL)).get(() -> getData(
                "/api/bot/guilds/" + URLEncoder.encode(guildId, StandardCharsets.UTF_8) + "/mappings",
                new TypeToken<List<RoleMapping>>() {}.getType()
        ));
    }

    /**
     * Les devoirs à rendre à partir de {@code from} pour les groupes du membre (ceux de son compte StudySuite), avec
     * ce qu'il a déjà coché.
     */
    public List<Assignment> getAssignments(String discordUserId, Instant from) {
        return send("GET", "/api/assignments?from=" + from, null, discordUserId,
                new TypeToken<List<Assignment>>() {}.getType());
    }

    public Assignment createAssignment(String discordUserId, NewAssignment assignment) {
        return send("POST", "/api/assignments", gson.toJson(assignment), discordUserId, Assignment.class);
    }

    /** Coche ou décoche un devoir pour ce membre. */
    public void setCompleted(String discordUserId, String assignmentId, boolean done) {
        send(done ? "POST" : "DELETE", "/api/assignments/" + URLEncoder.encode(assignmentId, StandardCharsets.UTF_8) + "/complete",
                null, discordUserId, JsonElement.class);
    }

    private <T> T getData(String path, Type type) {
        return send("GET", path, null, null, type);
    }

    /**
     * @param actingDiscordUserId le membre pour qui agir ({@code X-Acting-Discord-User}), ou null pour une route
     *                            publique ou une route du bot
     */
    private <T> T send(String method, String path, @Nullable String jsonBody, @Nullable String actingDiscordUserId, Type type) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        if (actingDiscordUserId != null) {
            if (apiKey == null) {
                throw new StudySuiteException("No StudySuite API key configured, cannot act for a member");
            }
            request.header("Authorization", "Bot " + apiKey);
            request.header("X-Acting-Discord-User", actingDiscordUserId);
        } else if (apiKey != null && path.startsWith("/api/bot/")) {
            request.header("Authorization", "Bot " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new StudySuiteException("StudySuite is unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudySuiteException("Interrupted while calling StudySuite", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            ApiError error = readError(response.body());
            if ("NOT_LINKED".equals(error.code())) {
                throw new StudySuiteNotLinkedException();
            }
            if (status == 403 || status == 404 || status == 400) {
                // Refus métier (compte en attente, groupe interdit…) : le message de l'API est destiné à l'utilisateur
                throw new StudySuiteRefusedException(status, error.code(), error.message());
            }
            logger.warn("{} {} answered {}: {}", method, path, status, response.body());
            throw new StudySuiteException("StudySuite answered " + status + " on " + path);
        }

        try {
            JsonElement data = gson.fromJson(response.body(), JsonObject.class).get("data");
            if (data == null) {
                throw new StudySuiteException("StudySuite response to " + path + " has no data");
            }
            return gson.fromJson(data, type);
        } catch (JsonParseException | IllegalStateException e) {
            throw new StudySuiteException("Unreadable StudySuite response to " + path, e);
        }
    }

    private record ApiError(@Nullable String code, @Nullable String message) {
    }

    private ApiError readError(String body) {
        try {
            JsonObject error = gson.fromJson(body, JsonObject.class).getAsJsonObject("error");
            return new ApiError(
                    error.has("code") ? error.get("code").getAsString() : null,
                    error.has("message") ? error.get("message").getAsString() : null
            );
        } catch (RuntimeException e) {
            return new ApiError(null, null);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Une valeur rechargée quand elle a expiré. Une erreur n'est pas mise en cache. */
    private static final class Expiring<T> {
        private final Duration ttl;
        private T value;
        private long expiresAt;

        Expiring(Duration ttl) {
            this.ttl = ttl;
        }

        synchronized T get(@NotNull Supplier<T> loader) {
            long now = System.nanoTime();
            if (value == null || now - expiresAt >= 0) {
                value = loader.get();
                expiresAt = now + ttl.toNanos();
            }
            return value;
        }
    }
}
