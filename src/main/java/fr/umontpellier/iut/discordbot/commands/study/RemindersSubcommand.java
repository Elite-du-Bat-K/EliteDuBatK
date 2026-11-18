package fr.umontpellier.iut.discordbot.commands.study;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.studysuite.ReminderFormatter;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteClient;
import fr.umontpellier.iut.discordbot.studysuite.model.ReminderSettings;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /study rappels} : le panneau des rappels de cours en message privé, toujours en privé.
 * <p>
 * Tout ce qu'il montre vient de StudySuite, interrogé au nom du membre : le bot ne garde rien. Un menu choisit le
 * délai (et active les rappels), un bouton les active ou les coupe, un autre envoie un message privé de test.
 * Identifiants : {@code study:rappels:<on|off|test|lead>}.
 */
public class RemindersSubcommand extends StudySubcommand {
    static final String NAME = "rappels";
    private static final String PREFIX = "study:" + NAME + ":";
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String TEST = "test";
    private static final String LEAD = "lead";

    public RemindersSubcommand(@NotNull Bot bot) {
        super(bot);
    }

    @NotNull
    @Override
    public SubcommandData getData() {
        return new SubcommandData(NAME, "Recevoir un message privé avant chacun de tes cours");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        replyLaterWith(event, true, () -> render(client().getReminderSettings(userId), null));
    }

    @Override
    public void onButton(ButtonInteractionEvent event) {
        String action = event.getComponentId().substring(PREFIX.length());
        String userId = event.getUser().getId();

        editLater(event, () -> {
            StudySuiteClient client = client();
            ReminderSettings settings;
            String notice = null;
            if (action.equals(ON)) {
                // Le délai affiché, celui que StudySuite propose par défaut tant que rien n'est choisi.
                settings = client.enableReminders(userId, client.getReminderSettings(userId).leadMinutes());
                notice = "🔔 Rappels activés.";
            } else if (action.equals(OFF)) {
                settings = client.disableReminders(userId);
                notice = "🔕 Rappels désactivés.";
            } else if (action.equals(TEST)) {
                notice = ReminderFormatter.testResult(client.sendTestReminder(userId));
                settings = client.getReminderSettings(userId);
            } else {
                settings = client.getReminderSettings(userId);
            }
            return render(settings, notice);
        });
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event) {
        String userId = event.getUser().getId();
        int lead = Integer.parseInt(event.getValues().getFirst());

        editLater(event, () -> render(
                client().enableReminders(userId, lead),
                "🔔 Rappels activés, " + lead + " min avant chaque cours."
        ));
    }

    private StudySuiteClient client() {
        StudySuiteClient client = getBot().getStudySuite();
        if (!client.hasApiKey()) {
            throw new UserFacingException("Les rappels ne sont pas disponibles : le bot n'a pas de clé StudySuite.");
        }
        return client;
    }

    private MessageEditData render(ReminderSettings settings, @Nullable String notice) {
        String siteUrl = getBot().getStudySuite().getBaseUrl();
        Container panel = Container.of(
                TextDisplay.of(ReminderFormatter.panel(settings, siteUrl, notice)),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(leadMenu(settings)),
                ActionRow.of(
                        settings.enabled()
                                ? Button.danger(PREFIX + OFF, "Désactiver")
                                : Button.success(PREFIX + ON, "Activer"),
                        Button.secondary(PREFIX + TEST, "M'envoyer un test")
                )
        );
        return new MessageEditBuilder().useComponentsV2(true).setComponents(List.of(panel)).build();
    }

    /** Le délai actuel est présélectionné ; en choisir un active aussi les rappels. */
    private static StringSelectMenu leadMenu(ReminderSettings settings) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(PREFIX + LEAD)
                .setPlaceholder(settings.enabled() ? "Changer le délai" : "Choisir un délai et activer");
        for (int minutes : settings.leadChoices()) {
            menu.addOption(minutes + " min avant le cours", String.valueOf(minutes));
        }
        if (settings.enabled()) {
            menu.setDefaultValues(String.valueOf(settings.leadMinutes()));
        }
        return menu.build();
    }
}
