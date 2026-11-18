package fr.umontpellier.iut.discordbot.commands.study;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** {@code /study} : tout ce qui vient de StudySuite (planning, devoirs…), une sous-commande par usage. */
public class StudyCommand extends AbstractCommandWithAutocomplete {
    private final List<StudySubcommand> subcommands;

    public StudyCommand(Bot bot) {
        super(bot);
        this.subcommands = List.of(
                new PlanningSubcommand(bot),
                new HomeworkSubcommand(bot),
                new AddHomeworkSubcommand(bot),
                new RemindersSubcommand(bot)
        );
    }

    @NotNull
    @Override
    public SlashCommandData getCommandInformation() {
        return Commands.slash("study", "Planning et infos de StudySuite")
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(subcommands.stream().map(StudySubcommand::getData).toList());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        find(event.getSubcommandName()).ifPresentOrElse(
                sub -> sub.execute(event),
                () -> event.reply("Sous-commande inconnue").setEphemeral(true).queue()
        );
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        find(event.getSubcommandName()).ifPresent(sub -> sub.autocomplete(event));
    }

    /** {@code study:<sous-commande>:…} */
    @Override
    public void onButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        find(parts.length > 1 ? parts[1] : null).ifPresentOrElse(
                sub -> sub.onButton(event),
                () -> event.reply("Ce bouton n'est plus actif.").setEphemeral(true).queue()
        );
    }

    /** {@code study:<sous-commande>:…} */
    @Override
    public void onStringSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        find(parts.length > 1 ? parts[1] : null).ifPresentOrElse(
                sub -> sub.onStringSelect(event),
                () -> event.reply("Ce menu n'est plus actif.").setEphemeral(true).queue()
        );
    }

    private Optional<StudySubcommand> find(String name) {
        return subcommands.stream().filter(s -> s.getName().equals(name)).findFirst();
    }
}
