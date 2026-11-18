package fr.umontpellier.iut.discordbot.studysuite.model;

import org.jetbrains.annotations.Nullable;

/**
 * Un rappel à envoyer en message privé, reçu sur le flux de StudySuite. Le texte est prêt à être envoyé tel quel.
 *
 * @param eventId  le cours, ou null pour un test
 * @param startsAt le début du cours (instant ISO 8601), ou null pour un test
 */
public record ReminderMessage(
        String discordUserId,
        @Nullable String eventId,
        String title,
        String body,
        @Nullable String startsAt
) {
}
