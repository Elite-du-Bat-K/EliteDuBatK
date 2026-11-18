package fr.umontpellier.iut.discordbot.studysuite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseParserTest {
    private static List<SseParser.Event> parse(String... lines) {
        SseParser parser = new SseParser();
        return Stream.of(lines).map(parser::accept).flatMap(Optional::stream).toList();
    }

    @Test
    void anEventEndsAtTheBlankLine() {
        assertEquals(
                List.of(new SseParser.Event("reminder", "{\"title\":\"Maths\"}")),
                parse("event: reminder", "data: {\"title\":\"Maths\"}", "")
        );
    }

    @Test
    void heartbeatsAndOtherFieldsAreIgnored() {
        assertTrue(parse(": ping", "", "retry: 3000", "id: 4", "").isEmpty());
    }

    @Test
    void severalDataLinesAreJoined() {
        assertEquals(List.of(new SseParser.Event("message", "a\nb")), parse("data: a", "data: b", ""));
    }

    @Test
    void theNameDoesNotLeakIntoTheNextEvent() {
        assertEquals(
                List.of(new SseParser.Event("ready", "{}"), new SseParser.Event("message", "x")),
                parse("event: ready", "data: {}", "", "data: x", "")
        );
    }

    @Test
    void anEventWithoutDataIsDropped() {
        assertTrue(parse("event: reminder", "").isEmpty());
    }
}
