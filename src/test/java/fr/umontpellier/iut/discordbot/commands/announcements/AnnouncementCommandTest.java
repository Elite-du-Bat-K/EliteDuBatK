package fr.umontpellier.iut.discordbot.commands.announcements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementCommandTest {

	/** Un texte seul en V1 lève une exception à la construction : l'envoi plantait au clic sur « Envoyer ». */
	@Test
	void previewEditsAreComponentsV2() {
		assertTrue(AnnouncementCommand.text("⏳ Envoi en cours…").isUsingComponentsV2());
	}

	/** Le séparateur est U+2502, comme dans les salons créés par les migrations, pas un pipe. */
	@Test
	void channelNameFollowsMigrationFormat() {
		assertEquals("g1\u2502annonces", AnnouncementCommand.channelName("G1"));
	}
}
