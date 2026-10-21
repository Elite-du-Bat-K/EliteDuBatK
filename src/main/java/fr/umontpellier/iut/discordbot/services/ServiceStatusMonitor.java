package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Vérifie les services de {@code statusTargets} à intervalle régulier et garde le dernier résultat de chacun, pour
 * que {@code /status} réponde sans attendre. L'historique est en mémoire : il repart de zéro à chaque démarrage.
 */
public class ServiceStatusMonitor {
	private final Logger logger = LoggerFactory.getLogger(ServiceStatusMonitor.class);
	private final List<StatusTarget> targets;
	private final Duration interval;
	private final ServiceStatusChecker checker;
	private final Clock clock;
	private final Instant startedAt;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ServiceStatusMonitor");
		thread.setDaemon(true);
		return thread;
	});

	/** Dans l'ordre de {@link #targets}, vide jusqu'à la fin de la première vérification. */
	private volatile List<TrackedStatus> statuses = List.of();

	public ServiceStatusMonitor(List<StatusTarget> targets, Duration interval) {
		this(targets, interval, new ServiceStatusChecker(), Clock.systemUTC());
	}

	public ServiceStatusMonitor(List<StatusTarget> targets, Duration interval, ServiceStatusChecker checker, Clock clock) {
		this.targets = List.copyOf(targets);
		this.interval = interval;
		this.checker = checker;
		this.clock = clock;
		this.startedAt = clock.instant();
	}

	public void start() {
		if (targets.isEmpty()) {
			logger.info("No status targets configured, service monitoring disabled");
		}

		if (!targets.isEmpty()) {
			logger.info("Monitoring {} services every {} s", targets.size(), interval.toSeconds());
			// Délai fixe : une vérification lente (essais retentés) ne se superpose jamais à la suivante.
			scheduler.scheduleWithFixedDelay(this::refresh, 0, interval.toSeconds(), TimeUnit.SECONDS);
		}
	}

	public boolean hasTargets() {
		return !targets.isEmpty();
	}

	public List<TrackedStatus> getStatuses() {
		return statuses;
	}

	private void refresh() {
		// Une exception non attrapée arrêterait définitivement la tâche planifiée.
		try {
			update(checker.checkAll(targets).join(), clock.instant());
		} catch (RuntimeException e) {
			logger.error("Unexpected error while checking services", e);
		}
	}

	void update(List<ServiceStatus> results, Instant now) {
		List<TrackedStatus> previous = statuses;
		List<TrackedStatus> updated = new ArrayList<>(results.size());

		for (int i = 0; i < results.size(); i++) {
			boolean isFirstCheck = previous.isEmpty();
			TrackedStatus tracked = isFirstCheck
					? TrackedStatus.first(results.get(i), startedAt, now)
					: previous.get(i).next(results.get(i), now);
			boolean hasChanged = !isFirstCheck && tracked.since().equals(now);

			if (hasChanged) {
				logger.info("Service {} is now {}", tracked.latest().target().getDisplayName(), tracked.latest().state());
			}

			updated.add(tracked);
		}

		statuses = List.copyOf(updated);
	}
}
