package fr.umontpellier.iut.discordbot.studysuite.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Un cours du planning.
 * <p>
 * {@code startDate} et {@code endDate} portent le décalage de Paris ({@code dateFormat=iso-offset}) : un cours à
 * 10h00 arrive en {@code 10:00:00.000+02:00} l'été, {@code +01:00} l'hiver. Ils donnent donc à la fois l'heure
 * affichée sur le planning ({@link #start()}) et le vrai instant ({@link #startInstant()}).
 */
public record StudyEvent(
        String id,
        String title,
        String startDate,
        String endDate,
        List<Room> rooms,
        List<Teacher> teachers,
        List<GroupRef> groups
) {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    public record Room(String id, String name) {
    }

    public record Teacher(String id, String firstName, String lastName) {
    }

    /** L'heure de Paris affichée sur le planning. */
    public LocalDateTime start() {
        return parisTime(startDate);
    }

    public LocalDateTime end() {
        return parisTime(endDate);
    }

    public Instant startInstant() {
        return OffsetDateTime.parse(startDate).toInstant();
    }

    public Instant endInstant() {
        return OffsetDateTime.parse(endDate).toInstant();
    }

    public List<Room> rooms() {
        return rooms == null ? List.of() : rooms;
    }

    public List<Teacher> teachers() {
        return teachers == null ? List.of() : teachers;
    }

    public List<GroupRef> groups() {
        return groups == null ? List.of() : groups;
    }

    /** Converti vers Paris plutôt que lu tel quel, pour rester juste si l'API renvoyait un autre décalage. */
    private static LocalDateTime parisTime(String date) {
        return OffsetDateTime.parse(date).atZoneSameInstant(PARIS).toLocalDateTime();
    }
}
