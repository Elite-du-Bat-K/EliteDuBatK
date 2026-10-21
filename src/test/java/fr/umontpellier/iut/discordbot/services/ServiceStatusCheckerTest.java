package fr.umontpellier.iut.discordbot.services;

import com.sun.net.httpserver.HttpServer;
import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceStatusCheckerTest {
	private final AtomicInteger flakyCalls = new AtomicInteger();
	private final AtomicInteger brokenCalls = new AtomicInteger();
	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.createContext("/broken", exchange -> {
			brokenCalls.incrementAndGet();
			exchange.sendResponseHeaders(503, -1);
			exchange.close();
		});
		server.createContext("/login", exchange -> {
			exchange.sendResponseHeaders(403, -1);
			exchange.close();
		});
		server.createContext("/flaky", exchange -> {
			exchange.sendResponseHeaders(flakyCalls.incrementAndGet() == 1 ? 503 : 200, -1);
			exchange.close();
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@ParameterizedTest
	@CsvSource({"199, UP", "302, UP", "403, UP", "499, UP", "500, DOWN", "503, DOWN"})
	void serverErrorsAreDown(int httpCode, ServiceStatus.State expected) {
		StatusTarget target = new StatusTarget("Moodle", "https://moodle.example", null);
		assertEquals(expected, ServiceStatus.fromResponse(target, httpCode, 10).state());
	}

	@Test
	void pingUrlFallsBackToPublicUrl() {
		assertEquals("https://public", new StatusTarget("A", "https://public", null).getCheckedUrl());
		assertEquals("https://public", new StatusTarget("A", "https://public", " ").getCheckedUrl());
		assertEquals("https://ping", new StatusTarget("A", "https://public", "https://ping").getCheckedUrl());
	}

	@Test
	void checksEveryTargetInOrder() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}

		List<StatusTarget> targets = List.of(
				new StatusTarget("ok", baseUrl + "/ok", null),
				new StatusTarget("broken", "https://shown.example", baseUrl + "/broken"),
				new StatusTarget("login", baseUrl + "/login", null),
				new StatusTarget("closed", "http://127.0.0.1:" + closedPort, null),
				new StatusTarget("invalid", "not a url", null),
				new StatusTarget("missing", null, null)
		);

		List<ServiceStatus.State> states = fastChecker().checkAll(targets).join().stream()
				.map(ServiceStatus::state)
				.toList();

		assertEquals(List.of(
				ServiceStatus.State.UP,
				ServiceStatus.State.DOWN,
				ServiceStatus.State.UP,
				ServiceStatus.State.UNREACHABLE,
				ServiceStatus.State.INVALID_URL,
				ServiceStatus.State.INVALID_URL
		), states);
	}

	@Test
	void oneFailureIsRetried() {
		ServiceStatus status = fastChecker().checkAll(List.of(new StatusTarget("flaky", baseUrl + "/flaky", null)))
				.join().getFirst();

		assertEquals(ServiceStatus.State.UP, status.state());
		assertEquals(2, flakyCalls.get());
	}

	@Test
	void persistentFailureStopsAfterLastAttempt() {
		ServiceStatus status = fastChecker().checkAll(List.of(new StatusTarget("broken", baseUrl + "/broken", null)))
				.join().getFirst();

		assertEquals(ServiceStatus.State.DOWN, status.state());
		assertEquals(3, brokenCalls.get());
	}

	private static ServiceStatusChecker fastChecker() {
		return new ServiceStatusChecker(3, Duration.ofMillis(10));
	}
}
