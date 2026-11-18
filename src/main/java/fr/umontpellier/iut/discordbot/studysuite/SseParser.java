package fr.umontpellier.iut.discordbot.studysuite;

import java.util.Optional;

/**
 * Découpe un flux Server-Sent Events, lu ligne par ligne, en événements. Seuls les champs {@code event} et
 * {@code data} servent ici ; les commentaires ({@code : ping}) et les autres champs sont ignorés.
 * <p>
 * Un objet par connexion : il garde l'événement en cours entre deux lignes.
 */
public final class SseParser {
    /** Un événement complet : son nom ({@code message} par défaut) et ses données. */
    public record Event(String name, String data) {
    }

    private String name = "message";
    private final StringBuilder data = new StringBuilder();
    private boolean hasData = false;

    /**
     * Lit une ligne du flux.
     *
     * @return l'événement que cette ligne vide termine, s'il porte des données
     */
    public Optional<Event> accept(String line) {
        Optional<Event> completed = Optional.empty();

        if (line.isEmpty()) {
            if (hasData) {
                completed = Optional.of(new Event(name, data.toString()));
            }
            name = "message";
            data.setLength(0);
            hasData = false;
        } else if (line.startsWith("event:")) {
            name = value(line, "event:");
        } else if (line.startsWith("data:")) {
            if (hasData) {
                data.append('\n');
            }
            data.append(value(line, "data:"));
            hasData = true;
        }

        return completed;
    }

    /** La valeur d'un champ, sans l'espace qui suit les deux-points. */
    private static String value(String line, String field) {
        String value = line.substring(field.length());
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
