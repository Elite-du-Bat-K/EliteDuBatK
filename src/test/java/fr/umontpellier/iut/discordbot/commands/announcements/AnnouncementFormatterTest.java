package fr.umontpellier.iut.discordbot.commands.announcements;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementFormatterTest {
	private static final String AVATAR = "https://cdn.discordapp.com/embed/avatars/0.png";
	private static final Instant SENT_AT = Instant.ofEpochSecond(1_800_000_000L);

	@Test
	void mentionsAreRoleMentions() {
		assertEquals("<@&1> <@&2>", AnnouncementFormatter.mentions(List.of("1", "2")));
		assertEquals("", AnnouncementFormatter.mentions(List.of()));
	}

	@Test
	void announcementIsSignedWithMentionsAndFooter() {
		Container container = AnnouncementFormatter.announcement("Mme Dupont", AVATAR, "Pas cours demain.",
				"<@&1>", List.of("G1", "G2"), SENT_AT, null);
		List<? extends ContainerChildComponent> children = container.getComponents();

		assertEquals(4, children.size());
		Section signed = assertInstanceOf(Section.class, children.getFirst());
		assertEquals("**Mme Dupont**\nPas cours demain.", ((TextDisplay) signed.getContentComponents().getFirst()).getContent());
		assertEquals("<@&1>", text(children.get(2)));
		assertEquals("-# Annonce pour G1 et G2 · <t:1800000000:f>", text(children.get(3)));
	}

	@Test
	void noMentionLineWithoutRoles() {
		Container container = AnnouncementFormatter.announcement("Mme Dupont", AVATAR, "Texte", "", List.of("G1"),
				SENT_AT, null);

		assertEquals(3, container.getComponents().size());
	}

	@Test
	void previewEndsWithActions() {
		ActionRow actions = ActionRow.of(Button.success("annonce:send:x", "Envoyer"));
		Container container = AnnouncementFormatter.announcement("Mme Dupont", AVATAR, "Texte", "", List.of("G1"),
				SENT_AT, actions);

		assertInstanceOf(ActionRow.class, container.getComponents().getLast());
	}

	@Test
	void summaryListsSentAndFailed() {
		assertEquals("✅ Annonce envoyée dans <#1> et <#2>.", AnnouncementFormatter.summary(List.of("<#1>", "<#2>"), List.of()));
		assertEquals("✅ Annonce envoyée dans <#1>.\n❌ G2 : salon d'annonces introuvable",
				AnnouncementFormatter.summary(List.of("<#1>"), List.of("G2 : salon d'annonces introuvable")));
		assertTrue(AnnouncementFormatter.summary(List.of(), List.of("G1 : x")).startsWith("❌ L'annonce n'a été envoyée nulle part."));
	}

	private static String text(ContainerChildComponent component) {
		return assertInstanceOf(TextDisplay.class, component).getContent();
	}
}
