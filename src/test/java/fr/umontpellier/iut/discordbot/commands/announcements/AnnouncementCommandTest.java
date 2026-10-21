package fr.umontpellier.iut.discordbot.commands.announcements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementCommandTest {

	/** Un texte seul en V1 lève une exception à la construction : l'envoi plantait au clic sur « Envoyer ». */
	@Test
	void previewEditsAreComponentsV2() {
		assertTrue(AnnouncementCommand.text("⏳ Envoi en cours…").isUsingComponentsV2());
	}
}
