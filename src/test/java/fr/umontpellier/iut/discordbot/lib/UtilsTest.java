package fr.umontpellier.iut.discordbot.lib;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsTest {

    public static Stream<Arguments> joinWithLastDifferentCases() {
        return Stream.of(
                Arguments.of(", ", " et ", null, ""),
                Arguments.of(", ", " et ", List.of(), ""),
                Arguments.of(", ", " et ", List.of(1, 2, 3, 4), "1, 2, 3 et 4"),
                Arguments.of(", ", " and ", List.of("only"), "only"),
                Arguments.of(", ", " and ", Collections.singletonList(null), "null"),
                Arguments.of(", ", " and ", List.of("one", "two", "three"), "one, two and three"),
                Arguments.of(" | ", " / ", List.of("1", "2"), "1 / 2")
        );
    }

    @ParameterizedTest
    @MethodSource("joinWithLastDifferentCases")
    public void joinWithLastDifferent(String mainSeparator, String lastSeparator, List<?> values, String expected) {
        assertEquals(expected, Utils.joinWithLastDifferent(mainSeparator, lastSeparator, values));
    }
}

