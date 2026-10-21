package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Vérifie que des services externes (Moodle, GitLab…) répondent, vus depuis le serveur du bot.
 * <p>
 * Un échec est retenté aussitôt, jusqu'à {@value #DEFAULT_ATTEMPTS} essais espacés de quelques secondes : un délai
 * dépassé isolé ne fait pas passer un service en panne. Les services sont vérifiés en parallèle.
 */
public class ServiceStatusChecker {
	private static final int DEFAULT_ATTEMPTS = 3;
	private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);
	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final Logger logger = LoggerFactory.getLogger(ServiceStatusChecker.class);
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final int attempts;
	private final Duration retryDelay;

	public ServiceStatusChecker() {
		this(DEFAULT_ATTEMPTS, DEFAULT_RETRY_DELAY);
	}

	public ServiceStatusChecker(int attempts, Duration retryDelay) {
		this.attempts = attempts;
		this.retryDelay = retryDelay;
	}

	/** Les résultats, dans l'ordre des services donnés. */
	public CompletableFuture<List<ServiceStatus>> checkAll(List<StatusTarget> targets) {
		List<CompletableFuture<ServiceStatus>> checks = targets.stream()
				.map(target -> checkWithRetries(target, attempts))
				.toList();

		return CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]))
				.thenApply(ignored -> checks.stream().map(CompletableFuture::join).toList());
	}

	/** Le premier succès, ou le dernier échec. Une URL invalide n'est pas retentée. */
	private CompletableFuture<ServiceStatus> checkWithRetries(StatusTarget target, int attemptsLeft) {
		return check(target).thenCompose(status -> {
			CompletableFuture<ServiceStatus> result = CompletableFuture.completedFuture(status);
			boolean isFinal = status.state() == ServiceStatus.State.UP
					|| status.state() == ServiceStatus.State.INVALID_URL
					|| attemptsLeft <= 1;

			if (!isFinal) {
				logger.info("Service {} failed a check ({}), retrying", target.getDisplayName(), status.state());
				result = CompletableFuture.supplyAsync(() -> null,
								CompletableFuture.delayedExecutor(retryDelay.toMillis(), TimeUnit.MILLISECONDS))
						.thenCompose(ignored -> checkWithRetries(target, attemptsLeft - 1));
			}

			return result;
		});
	}

	private CompletableFuture<ServiceStatus> check(StatusTarget target) {
		CompletableFuture<ServiceStatus> result = CompletableFuture.completedFuture(ServiceStatus.invalidUrl(target));
		long start = System.nanoTime();

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(target.getCheckedUrl()))
					.timeout(TIMEOUT)
					.GET()
					.build();

			result = http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
					.thenApply(response -> ServiceStatus.fromResponse(target, response.statusCode(),
							Duration.ofNanos(System.nanoTime() - start).toMillis()))
					.exceptionally(error -> {
						logger.info("Service {} unreachable: {}", target.getDisplayName(), error.toString());
						return ServiceStatus.unreachable(target);
					});
		} catch (IllegalArgumentException | NullPointerException e) {
			logger.warn("Invalid status URL for service {}: {}", target.getDisplayName(), target.getCheckedUrl());
		}

		return result;
	}
}
