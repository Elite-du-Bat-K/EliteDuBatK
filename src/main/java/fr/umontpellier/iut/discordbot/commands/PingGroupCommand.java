package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import fr.umontpellier.iut.discordbot.lib.Utils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class PingGroupCommand extends AbstractCommandWithAutocomplete {

    public PingGroupCommand(Bot bot) {
        super(bot);
    }

    @NotNull
    @Override
    public SlashCommandData getCommandInformation() {
        return Commands.slash("ping-group", "Mentionner un groupe").addOption(OptionType.STRING, "group", "Le groupe à mentionner", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = Objects.requireNonNull(event.getMember());
        Guild guild = Objects.requireNonNull(event.getGuild());
        String group = Objects.requireNonNull(event.getOption("group")).getAsString();

        logger.debug("User {} wants to ping group {}", member.getId(), group);
        logger.debug("Available groups are {}", getBot().getConfig().get().getRoles());

        List<String> correspondingGroup = getCorrespondingGroup(group);
        if (correspondingGroup.isEmpty()) {
            event.reply("Je n'ai pas trouvé le groupe demandé").setEphemeral(true).queue();
            return;
        }

        List<String> mentions = correspondingGroup.stream()
                .map(guild::getRoleById)
                .filter(Objects::nonNull)
                .map(Role::getAsMention)
                .toList();

        event.reply(String.format("%s veut mentionner %s", member.getAsMention(), Utils.joinWithLastDifferent(", ", " et ", mentions))).queue();
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        String inputGroup = Objects.requireNonNull(event.getOption("group")).getAsString();

        List<String> correspondingGroups = getBot().getConfig().get().getRoles().stream()
                .filter(group -> group.contains(inputGroup.toLowerCase()))
                .toList();

        event.replyChoices(
                correspondingGroups
                        .stream()
                        .map(group -> new Command.Choice(group, group))
                        .toList()
        ).queue();
    }

    private List<String> getCorrespondingGroup(String group) {
        return getBot().getConfig()
                .get()
                .getRoles()
                .stream()
                .filter(g -> g.contains(group))
                .findFirst()
                .map(matchingGroup -> getBot().getConfig().get().getRolesIdForGroup(matchingGroup))
                .orElse(List.of());
    }
}
