package fr.umontpellier.iut.discordbot.studysuite;

import fr.umontpellier.iut.discordbot.studysuite.model.GroupRef;
import fr.umontpellier.iut.discordbot.studysuite.model.StudyEvent;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningFormatterTest {
    private static final String SITE = "https://study-info.umontp.fr";
    private static final GroupRef S1A = new GroupRef("s1a", "S1a", null);
    private static final GroupRef PROMO = new GroupRef("promo", "BUT 1A PROMO", "BUT1");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    private static StudyEvent event(String title, String start, String end, GroupRef group) {
        return new StudyEvent(title, title, start, end,
                List.of(new StudyEvent.Room("r", "K018")),
                List.of(new StudyEvent.Teacher("t", "Anne", "DAUMET")),
                List.of(group));
    }

    @Test
    void hoursAreDiscordTimestampsOfTheParisHour() {
        // 08:00 à Paris, soit 06:00 UTC en heure d'été
        StudyEvent summer = event("Maths", "2026-09-14T08:00:00.000+02:00", "2026-09-14T10:30:00.000+02:00", S1A);
        assertEquals("<t:1789365600:t> – <t:1789374600:t>", PlanningFormatter.hours(summer));

        // … et 07:00 UTC en heure d'hiver
        StudyEvent winter = event("Maths", "2026-12-14T08:00:00.000+01:00", "2026-12-14T09:00:00.000+01:00", S1A);
        assertTrue(PlanningFormatter.hours(winter).startsWith("<t:1797231600:t>"), PlanningFormatter.hours(winter));
    }

    @Test
    void inheritedCoursesShowTheirGroup() {
        StudyEvent own = event("TP", "2026-09-14T08:00:00.000+02:00", "2026-09-14T10:00:00.000+02:00", S1A);
        StudyEvent inherited = event("CM", "2026-09-14T10:00:00.000+02:00", "2026-09-14T12:00:00.000+02:00", PROMO);

        assertEquals("K018 · A. DAUMET", PlanningFormatter.details(own, Set.of("s1a")));
        assertEquals("K018 · A. DAUMET · BUT1", PlanningFormatter.details(inherited, Set.of("s1a")));
    }

    @Test
    void teacherWithoutFirstName() {
        assertEquals("DAUMET", PlanningFormatter.teacher(new StudyEvent.Teacher("t", "", "DAUMET")));
        assertEquals("A. DAUMET", PlanningFormatter.teacher(new StudyEvent.Teacher("t", "Anne", "DAUMET")));
    }

    @Test
    void dayIsSortedByHour() {
        MessageEmbed embed = PlanningFormatter.day(MONDAY, List.of(S1A), List.of(
                event("Après-midi", "2026-09-14T14:00:00.000+02:00", "2026-09-14T16:00:00.000+02:00", S1A),
                event("Matin", "2026-09-14T08:00:00.000+02:00", "2026-09-14T10:00:00.000+02:00", S1A)
        ), SITE, SITE + "/planning?group=S1a");

        assertEquals("Planning S1a · lundi 14 septembre 2026", embed.getTitle());
        assertEquals(SITE + "/planning?group=S1a", embed.getUrl());
        String description = embed.getDescription();
        assertTrue(description.indexOf("Matin") < description.indexOf("Après-midi"), description);
    }

    @Test
    void emptyDay() {
        MessageEmbed embed = PlanningFormatter.day(MONDAY, List.of(S1A), List.of(), SITE, SITE + "/planning?group=S1a");
        assertEquals("Aucun cours ce jour-là 🎉", embed.getDescription());
    }

    @Test
    void weekHasOneFieldPerWeekdayAndWeekendOnlyWhenBusy() {
        MessageEmbed embed = PlanningFormatter.week(MONDAY, List.of(S1A), List.of(
                event("Lundi", "2026-09-14T08:00:00.000+02:00", "2026-09-14T10:00:00.000+02:00", S1A),
                event("Samedi", "2026-09-19T08:00:00.000+02:00", "2026-09-19T10:00:00.000+02:00", S1A)
        ), SITE, SITE + "/planning?group=S1a");

        assertEquals("Planning S1a · semaine du 14 septembre", embed.getTitle());
        assertEquals(
                List.of("Lundi 14/09", "Mardi 15/09", "Mercredi 16/09", "Jeudi 17/09", "Vendredi 18/09", "Samedi 19/09"),
                embed.getFields().stream().map(MessageEmbed.Field::getName).toList()
        );
        assertEquals("*Pas de cours*", embed.getFields().get(1).getValue());
        assertEquals("**<t:1789365600:t> – <t:1789372800:t>** Lundi · K018 · A. DAUMET", embed.getFields().getFirst().getValue());
    }

    @Test
    void emptyWeek() {
        MessageEmbed embed = PlanningFormatter.week(MONDAY, List.of(S1A), List.of(), SITE, SITE + "/planning?group=S1a");
        assertEquals("Aucun cours cette semaine 🎉", embed.getDescription());
        assertTrue(embed.getFields().isEmpty());
    }

    @Test
    void crowdedDayStaysUnderTheFieldLimit() {
        List<String> lines = Collections.nCopies(40, "`08:00 – 10:00` " + "x".repeat(40));
        String value = PlanningFormatter.fitField(lines);

        assertTrue(value.length() <= MessageEmbed.VALUE_MAX_LENGTH, "length " + value.length());
        assertTrue(value.endsWith("autre(s)*"), value);
    }

    @Test
    void embedIsAcceptedByDiscord() {
        List<StudyEvent> many = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> event("Cours " + "y".repeat(80) + i, "2026-09-1" + (4 + i % 5) + "T08:00:00.000+02:00", "2026-09-1" + (4 + i % 5) + "T10:00:00.000+02:00", S1A))
                .toList();

        assertTrue(PlanningFormatter.week(MONDAY, List.of(S1A), many, SITE, SITE + "/planning?group=S1a").isSendable());
        assertTrue(PlanningFormatter.day(MONDAY, List.of(S1A), many, SITE, SITE + "/planning?group=S1a").isSendable());
    }
}
