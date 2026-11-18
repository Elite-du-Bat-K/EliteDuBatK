package fr.umontpellier.iut.discordbot.studysuite;

import fr.umontpellier.iut.discordbot.studysuite.model.ReminderMessage;
import fr.umontpellier.iut.discordbot.studysuite.model.ReminderSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderFormatterTest {
    private static final String SITE = "https://study-info.umontp.fr";
    private static final ReminderSettings.Group G1 = new ReminderSettings.Group("g1", "G1");

    private static ReminderSettings settings(boolean enabled, String blocker, boolean botConnected, String lastFailure) {
        return new ReminderSettings(enabled, 15, List.of(5, 10, 15, 30, 60), G1, blocker, botConnected,
                "2026-11-18T07:45:00.000Z", lastFailure, lastFailure == null ? null : "2026-11-18T08:45:00.000Z");
    }

    @Test
    void theDmCarriesTheCourseAndARelativeStart() {
        ReminderMessage message = new ReminderMessage("1", "e", "Maths", "Dans 15 min · 08:00 · K018",
                "2026-11-18T07:00:00.000Z");
        assertEquals("### ⏰ Maths\nDans 15 min · 08:00 · K018\nDébut <t:1794985200:R>", ReminderFormatter.dm(message));
    }

    @Test
    void aTestDmHasNoStart() {
        ReminderMessage message = new ReminderMessage("1", null, "Rappels de cours", "Ceci est un test", null);
        assertEquals("### ⏰ Rappels de cours\nCeci est un test", ReminderFormatter.dm(message));
    }

    @Test
    void anEnabledPanelShowsTheLeadAndTheLastDelivery() {
        String panel = ReminderFormatter.panel(settings(true, null, true, null), SITE, null);
        assertTrue(panel.contains("**activés**, 15 min avant"), panel);
        assertTrue(panel.contains("Classe suivie : **G1**"), panel);
        assertTrue(panel.contains("✅ Dernier rappel envoyé <t:"), panel);
        assertFalse(panel.contains("⚠️"), panel);
    }

    @Test
    void aFailureWinsOverTheLastDelivery() {
        String panel = ReminderFormatter.panel(settings(true, null, true, "Messages privés fermés"), SITE, null);
        assertTrue(panel.contains("❌ Dernier échec <t:"), panel);
        assertTrue(panel.contains("Messages privés fermés"), panel);
        assertFalse(panel.contains("✅"), panel);
    }

    @Test
    void theBlockerAndADisconnectedBotAreSaid() {
        String panel = ReminderFormatter.panel(settings(true, "no-class", false, null), SITE, "📨 Test envoyé");
        assertTrue(panel.contains(SITE + "/profile"), panel);
        assertTrue(panel.contains("pas connecté"), panel);
        assertTrue(panel.contains("📨 Test envoyé"), panel);
    }

    @Test
    void aDisabledPanelDoesNotWorryAboutTheBot() {
        String panel = ReminderFormatter.panel(settings(false, null, false, null), SITE, null);
        assertTrue(panel.contains("**désactivés**"), panel);
        assertFalse(panel.contains("pas connecté"), panel);
    }
}
