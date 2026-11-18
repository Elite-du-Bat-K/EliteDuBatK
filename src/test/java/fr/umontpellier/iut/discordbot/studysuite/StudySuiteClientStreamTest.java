package fr.umontpellier.iut.discordbot.studysuite;

import com.sun.net.httpserver.HttpServer;
import fr.umontpellier.iut.discordbot.config.ConfigStructure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudySuiteClientStreamTest {
    private static final String KEY = "test-key-0123456789abcdef0123456789abcdef";

    private HttpServer server;
    private String baseUrl;
    private final CountDownLatch serverDone = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Un flux qui envoie « ready » puis se tait, comme une connexion morte côté réseau.
        server.createContext("/api/bot/reminders/stream", exchange -> {
            if (!("Bot " + KEY).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream body = exchange.getResponseBody();
            body.write("event: ready\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
            body.flush();
            try {
                serverDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        serverDone.countDown();
        server.stop(0);
    }

    @Test
    void theStreamDeliversEventsAndClosingItUnblocksTheReader() throws Exception {
        StudySuiteClient client = new StudySuiteClient(new ConfigStructure.StudySuiteConfig(baseUrl, KEY));
        Stream<String> lines = client.openReminderStream();
        SseParser parser = new SseParser();
        List<SseParser.Event> events = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(1);

        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try {
                lines.forEach(line -> parser.accept(line).ifPresent(event -> {
                    events.add(event);
                    ready.countDown();
                }));
            } catch (RuntimeException ignored) {
                // Fermer le flux depuis un autre thread peut se traduire par une exception de lecture.
            }
        });

        assertTrue(ready.await(5, TimeUnit.SECONDS), "the ready event never came");
        assertEquals("ready", events.getFirst().name());

        // Le serveur se tait : fermer le flux, comme le fait le chien de garde, doit libérer le lecteur.
        lines.close();
        reader.get(5, TimeUnit.SECONDS);
    }

    @Test
    void aWrongKeyIsAnError() {
        StudySuiteClient client = new StudySuiteClient(new ConfigStructure.StudySuiteConfig(baseUrl, "wrong"));
        assertThrows(StudySuiteException.class, client::openReminderStream);
    }
}
