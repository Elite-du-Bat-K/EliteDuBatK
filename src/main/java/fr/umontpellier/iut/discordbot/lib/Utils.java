package fr.umontpellier.iut.discordbot.lib;

import java.util.List;

public class Utils {
    public static String joinWithLastDifferent(String mainSeparator, String lastSeparator, List<?> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        if (elements.size() == 1) {
            return String.valueOf(elements.getFirst());
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                builder.append(i == elements.size() - 1 ? lastSeparator : mainSeparator);
            }
            builder.append(elements.get(i));
        }

        return builder.toString();
    }
}
