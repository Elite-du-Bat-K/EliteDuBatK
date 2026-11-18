package fr.umontpellier.iut.discordbot.studysuite;

import fr.umontpellier.iut.discordbot.studysuite.model.ReminderMessage;
import fr.umontpellier.iut.discordbot.studysuite.model.ReminderSettings;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Les textes des rappels de cours : le message privé envoyé, et le panneau de {@code /study rappels}. */
public final class ReminderFormatter {
    private ReminderFormatter() {
    }

    /** Le message privé d'un rappel : le titre du cours, puis le texte de StudySuite et le début en temps relatif. */
    public static String dm(ReminderMessage message) {
        String text = "### ⏰ " + message.title() + "\n" + message.body();
        if (message.startsAt() != null) {
            text += "\nDébut " + TimeFormat.RELATIVE.format(Instant.parse(message.startsAt()));
        }
        return text;
    }

    /**
     * Le texte du panneau.
     *
     * @param notice le résultat de la dernière action (test envoyé, délai changé…), ou null
     */
    public static String panel(ReminderSettings settings, String siteUrl, @Nullable String notice) {
        List<String> lines = new ArrayList<>();
        lines.add("### ⏰ Rappels de cours en message privé");
        lines.add(settings.enabled()
                ? "Statut : **activés**, " + settings.leadMinutes() + " min avant chaque cours"
                : "Statut : **désactivés**");
        if (settings.studentGroup() != null) {
            lines.add("Classe suivie : **" + settings.studentGroup().name() + "**, avec les cours de toute ta promo");
        }

        String blocker = blockerText(settings.blocker(), siteUrl);
        if (blocker != null) {
            lines.add("⚠️ " + blocker);
        }
        if (settings.enabled() && !settings.botConnected()) {
            lines.add("⚠️ Le bot n'est pas connecté à StudySuite pour l'instant : les rappels reprendront à sa reconnexion.");
        }
        if (settings.lastFailure() != null) {
            lines.add("❌ Dernier échec" + since(settings.lastFailureAt()) + " : " + settings.lastFailure());
        } else if (settings.lastDeliveredAt() != null) {
            lines.add("✅ Dernier rappel envoyé" + since(settings.lastDeliveredAt()));
        }
        if (notice != null) {
            lines.add(notice);
        }
        lines.add("-# Les cours masqués sur le site ne s'appliquent pas ici.");

        return String.join("\n", lines);
    }

    /** Ce que le membre doit faire pour que les rappels partent, ou null si rien ne bloque. */
    static @Nullable String blockerText(@Nullable String blocker, String siteUrl) {
        String text = null;
        if ("not-approved".equals(blocker)) {
            text = "Ton compte StudySuite attend la validation d'un admin : aucun rappel ne partira avant.";
        } else if ("no-class".equals(blocker)) {
            text = "Aucune classe sur ton compte StudySuite : choisis-la sur " + siteUrl + "/profile.";
        } else if ("no-discord".equals(blocker)) {
            text = "Aucun compte Discord n'est lié à ton compte StudySuite.";
        }
        return text;
    }

    /** Le résultat d'un test, à afficher dans le panneau. */
    public static String testResult(String result) {
        String text = "⚠️ Le test n'a pas pu être envoyé.";
        if ("sent".equals(result)) {
            text = "📨 Test envoyé : regarde tes messages privés.";
        } else if ("no-bot".equals(result)) {
            text = "⚠️ Le bot n'est pas connecté à StudySuite pour l'instant, réessaie dans un moment.";
        }
        return text;
    }

    private static String since(@Nullable String instant) {
        return instant == null ? "" : " " + TimeFormat.RELATIVE.format(Instant.parse(instant));
    }
}
