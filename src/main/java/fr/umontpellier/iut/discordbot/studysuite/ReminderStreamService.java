package fr.umontpellier.iut.discordbot.studysuite;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.studysuite.model.ReminderMessage;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Reçoit de StudySuite les rappels de cours à envoyer et les envoie en message privé.
 * <p>
 * StudySuite décide de tout (qui, quel cours, quand) et pousse chaque rappel sur un flux Server-Sent Events que ce
 * service garde ouvert. Le bot ne stocke rien : il envoie le message, puis dit à StudySuite s'il est arrivé.
 * <p>
 * Le flux est rouvert après toute coupure, avec une attente qui double à chaque échec (1 s, 2 s… jusqu'à 60 s).
 * StudySuite envoie un signe de vie toutes les 25 s : sans nouvelle depuis {@link #SILENCE_LIMIT}, la connexion est
 * tenue pour morte et refermée.
 */
public class ReminderStreamService {
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(70);
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final Logger logger = LoggerFactory.getLogger(ReminderStreamService.class);
    private final Gson gson = new Gson();
    private final Bot bot;
    private final AtomicBoolean started = new AtomicBoolean(false);
    /** Les comptes rendus d'envoi sont des appels bloquants : jamais sur le thread de JDA. */
    private final ExecutorService reports = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ReminderStreamWatchdog");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Stream<String> currentStream;
    private volatile long lastActivityNanos = System.nanoTime();
    private volatile long backoffSeconds = 1;

    public ReminderStreamService(Bot bot) {
        this.bot = bot;
    }

    /** Ouvre le flux, une seule fois même si JDA se reconnecte. Sans clé d'API, les rappels restent coupés. */
    public void start() {
        boolean canStart = bot.getStudySuite().hasApiKey() && started.compareAndSet(false, true);
        if (canStart) {
            logger.info("Opening the StudySuite reminder stream");
            watchdog.scheduleWithFixedDelay(this::closeIfSilent, 15, 15, TimeUnit.SECONDS);
            Thread.ofVirtual().name("ReminderStream").start(this::run);
        }
        if (!bot.getStudySuite().hasApiKey()) {
            logger.info("No StudySuite API key configured, course reminders by DM are off");
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                listenOnce();
                logger.warn("StudySuite closed the reminder stream, reconnecting in {} s", backoffSeconds);
            } catch (RuntimeException e) {
                logger.warn("Reminder stream lost ({}), reconnecting in {} s", e.getMessage(), backoffSeconds);
            }
            sleep(backoffSeconds);
            backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
        }
    }

    private void listenOnce() {
        try (Stream<String> lines = bot.getStudySuite().openReminderStream()) {
            currentStream = lines;
            lastActivityNanos = System.nanoTime();
            SseParser parser = new SseParser();
            lines.forEach(line -> {
                lastActivityNanos = System.nanoTime();
                parser.accept(line).ifPresent(this::handle);
            });
        } finally {
            currentStream = null;
        }
    }

    private void handle(SseParser.Event event) {
        if ("ready".equals(event.name())) {
            logger.info("StudySuite reminder stream connected");
            backoffSeconds = 1;
        } else if ("reminder".equals(event.name())) {
            try {
                deliver(gson.fromJson(event.data(), ReminderMessage.class));
            } catch (JsonParseException e) {
                logger.warn("Unreadable reminder from StudySuite: {}", e.getMessage());
            }
        }
    }

    private void deliver(ReminderMessage message) {
        bot.getJda().retrieveUserById(message.discordUserId())
                .flatMap(User::openPrivateChannel)
                .flatMap(channel -> channel
                        .sendMessageComponents(List.of(Container.of(TextDisplay.of(ReminderFormatter.dm(message)))))
                        .useComponentsV2())
                .queue(
                        sent -> report(message, true, null),
                        error -> report(message, false, failureReason(error))
                );
    }

    private void report(ReminderMessage message, boolean ok, String error) {
        reports.execute(() -> {
            try {
                bot.getStudySuite().reportReminderDelivery(message.discordUserId(), message.eventId(), ok, error);
            } catch (RuntimeException e) {
                logger.warn("Could not report a reminder delivery to StudySuite: {}", e.getMessage());
            }
        });
    }

    /** Pourquoi Discord a refusé le message, dit pour le membre : c'est ce que son panneau affichera. */
    static String failureReason(Throwable error) {
        String reason = "Discord a refusé le message.";
        if (error instanceof ErrorResponseException response) {
            if (response.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER) {
                reason = "Tes messages privés sont fermés : autorise les messages privés des membres d'un serveur "
                        + "que tu partages avec le bot.";
            } else if (response.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                reason = "Discord ne trouve plus ton compte.";
            } else {
                reason = "Discord a refusé le message (" + response.getErrorCode() + ").";
            }
        }
        return reason;
    }

    private void closeIfSilent() {
        Stream<String> stream = currentStream;
        boolean silent = System.nanoTime() - lastActivityNanos > SILENCE_LIMIT.toNanos();
        if (stream != null && silent) {
            logger.warn("No news from the reminder stream for {} s, closing it", SILENCE_LIMIT.toSeconds());
            stream.close();
        }
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
