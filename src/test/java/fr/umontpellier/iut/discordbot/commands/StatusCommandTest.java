package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import fr.umontpellier.iut.discordbot.services.ServiceStatus;
import fr.umontpellier.iut.discordbot.services.TrackedStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusCommandTest {
	private static final StatusTarget MOODLE = new StatusTarget("Moodle", "https://moodle.example", null);
	private static final Instant SINCE = Instant.ofEpochSecond(1_800_000_000L);
	private static final Instant CHECKED = Instant.ofEpochSecond(1_800_000_600L);

	@Test
	void upShowsSinceCodeAndLatency() {
		assertEquals("🟢 [Moodle](<https://moodle.example>) : en ligne depuis <t:1800000000:R> (HTTP 200, 312 ms)",
				StatusCommand.formatLine(changed(ServiceStatus.fromResponse(MOODLE, 200, 312))));
	}

	@Test
	void unchangedSinceStartupSaysAtLeast() {
		TrackedStatus tracked = TrackedStatus.first(ServiceStatus.fromResponse(MOODLE, 502, 40), SINCE, CHECKED);
		assertEquals("🔴 [Moodle](<https://moodle.example>) : en panne depuis au moins <t:1800000000:R> (HTTP 502)",
				StatusCommand.formatLine(tracked));
	}

	@Test
	void unreachable() {
		assertEquals("🔴 [Moodle](<https://moodle.example>) : injoignable depuis <t:1800000000:R> (pas de réponse)",
				StatusCommand.formatLine(changed(ServiceStatus.unreachable(MOODLE))));
	}

	@Test
	void invalidUrlHasNoSinceAndNoLinkWithoutPublicUrl() {
		StatusTarget target = new StatusTarget("GitLab", "", "https://gitlab.example/-/health");
		assertEquals("⚠️ **GitLab** : URL invalide dans la config",
				StatusCommand.formatLine(changed(ServiceStatus.invalidUrl(target))));
	}

	@Test
	void reportEndsWithLastCheck() {
		String report = StatusCommand.formatReport(List.of(changed(ServiceStatus.fromResponse(MOODLE, 200, 1))));
		assertEquals("-# Vérifié <t:1800000600:R>. Si c'est vert ici mais que ça ne marche pas chez toi, le problème vient sans doute de ta connexion.",
				report.lines().toList().getLast());
	}

	private static TrackedStatus changed(ServiceStatus status) {
		return new TrackedStatus(status, SINCE, false, CHECKED);
	}
}
