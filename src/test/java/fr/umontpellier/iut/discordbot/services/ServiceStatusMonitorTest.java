package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceStatusMonitorTest {
	private static final Instant STARTED_AT = Instant.parse("2026-10-21T08:00:00Z");
	private static final StatusTarget MOODLE = new StatusTarget("Moodle", "https://moodle.example", null);
	private static final StatusTarget GITLAB = new StatusTarget("GitLab", "https://gitlab.example", null);

	private final ServiceStatusMonitor monitor = new ServiceStatusMonitor(List.of(MOODLE, GITLAB), Duration.ofMinutes(1),
			new ServiceStatusChecker(), Clock.fixed(STARTED_AT, ZoneOffset.UTC));

	@Test
	void noStatusBeforeFirstCheck() {
		assertTrue(monitor.getStatuses().isEmpty());
	}

	@Test
	void firstCheckDatesFromStartup() {
		Instant firstCheck = STARTED_AT.plusSeconds(2);
		monitor.update(List.of(up(MOODLE), down(GITLAB)), firstCheck);

		TrackedStatus moodle = monitor.getStatuses().getFirst();
		assertEquals(STARTED_AT, moodle.since());
		assertTrue(moodle.sinceStartup());
		assertEquals(firstCheck, moodle.checkedAt());
	}

	@Test
	void sameStateKeepsSince() {
		monitor.update(List.of(up(MOODLE), down(GITLAB)), STARTED_AT);
		Instant later = STARTED_AT.plusSeconds(600);
		monitor.update(List.of(up(MOODLE), ServiceStatus.unreachable(GITLAB)), later);

		TrackedStatus gitlab = monitor.getStatuses().get(1);
		assertEquals(STARTED_AT, gitlab.since());
		assertTrue(gitlab.sinceStartup());
		assertEquals(later, gitlab.checkedAt());
		assertEquals(ServiceStatus.State.UNREACHABLE, gitlab.latest().state());
	}

	@Test
	void changeResetsSince() {
		monitor.update(List.of(up(MOODLE), up(GITLAB)), STARTED_AT);
		Instant outage = STARTED_AT.plusSeconds(3600);
		monitor.update(List.of(down(MOODLE), up(GITLAB)), outage);
		monitor.update(List.of(down(MOODLE), up(GITLAB)), outage.plusSeconds(60));

		TrackedStatus moodle = monitor.getStatuses().getFirst();
		assertEquals(outage, moodle.since());
		assertFalse(moodle.sinceStartup());
		assertTrue(monitor.getStatuses().get(1).sinceStartup());
	}

	private static ServiceStatus up(StatusTarget target) {
		return ServiceStatus.fromResponse(target, 200, 50);
	}

	private static ServiceStatus down(StatusTarget target) {
		return ServiceStatus.fromResponse(target, 503, 50);
	}
}
