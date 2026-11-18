package fr.umontpellier.iut.discordbot.studysuite.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Les rappels de cours en message privé d'un membre, tels que StudySuite les décrit : tout ce que montre le panneau
 * de {@code /study rappels}. Le bot n'en garde aucune copie.
 *
 * @param blocker ce qui empêcherait l'envoi même une fois activés ({@code not-approved}, {@code no-class},
 *                {@code no-discord}), ou null
 */
public record ReminderSettings(
        boolean enabled,
        int leadMinutes,
        List<Integer> leadChoices,
        @Nullable Group studentGroup,
        @Nullable String blocker,
        boolean botConnected,
        @Nullable String lastDeliveredAt,
        @Nullable String lastFailure,
        @Nullable String lastFailureAt
) {
    public record Group(String id, String name) {
    }

    public List<Integer> leadChoices() {
        return leadChoices == null ? List.of() : leadChoices;
    }
}
