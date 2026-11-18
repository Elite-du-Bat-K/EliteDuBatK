package fr.umontpellier.iut.discordbot.studysuite.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyEventTest {
    private static StudyEvent event(String start, String end) {
        return new StudyEvent("e", "Maths", start, end, List.of(), List.of(), List.of());
    }

    @Test
    void summerCourseKeepsItsParisHourAndInstant() {
        StudyEvent e = event("2026-09-14T08:00:00.000+02:00", "2026-09-14T10:30:00.000+02:00");

        assertEquals(LocalDateTime.of(2026, 9, 14, 8, 0), e.start());
        assertEquals(LocalDateTime.of(2026, 9, 14, 10, 30), e.end());
        assertEquals(Instant.parse("2026-09-14T06:00:00Z"), e.startInstant());
    }

    @Test
    void winterCourseKeepsItsParisHourAndInstant() {
        StudyEvent e = event("2026-12-14T08:00:00.000+01:00", "2026-12-14T09:00:00.000+01:00");

        assertEquals(LocalDateTime.of(2026, 12, 14, 8, 0), e.start());
        assertEquals(Instant.parse("2026-12-14T07:00:00Z"), e.startInstant());
    }

    @Test
    void aRealUtcInstantIsShownAtItsParisHour() {
        StudyEvent e = event("2026-12-14T07:00:00.000Z", "2026-12-14T08:00:00.000Z");

        assertEquals(LocalDateTime.of(2026, 12, 14, 8, 0), e.start());
        assertEquals(Instant.parse("2026-12-14T07:00:00Z"), e.startInstant());
    }
}
