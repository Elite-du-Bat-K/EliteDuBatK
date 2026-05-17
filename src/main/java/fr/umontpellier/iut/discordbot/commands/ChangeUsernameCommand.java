package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ChangeUsernameCommand extends AbstractCommandWithAutocomplete {
	public ChangeUsernameCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("change-username", "Change ton nom d'utilisateur")
				.addOption(OptionType.STRING, "username", "Le nouveau nom d'utilisateur", true);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String userId = event.getUser().getId();
		String newUsername = Objects.requireNonNull(event.getOption("username")).getAsString();

		this.getMember(event).ifPresentOrElse(
				member -> member.modifyNickname(newUsername).queue(
						success -> event.reply("Ton nom d'utilisateur a été changé en " + newUsername).setEphemeral(true).queue(),
						error -> {
							logger.error("Erreur lors du changement de nom d'utilisateur pour l'utilisateur {}: {}", userId, error.getMessage());
							event.reply("Une erreur est survenue lors du changement de ton nom d'utilisateur.").setEphemeral(true).queue();
						}),
				() -> event.reply("Une erreur est survenue.").setEphemeral(true).queue()
		);
	}

	@Override
	public void autocomplete(CommandAutoCompleteInteractionEvent event) {
		OptionMapping op = event.getOption("username");

		Optional<Member> memberOpt = getMember(event);
		if (memberOpt.isEmpty()) {
			event.replyChoices(List.of()).queue();
			return;
		}

		Member member = memberOpt.get();
		String proposedUsername = op != null ? op.getAsString() : "";
		String userName = member.getUser().getName();
		String userNickname = member.getNickname();

		List<String> choices = new ArrayList<>();

		if (userName.startsWith(proposedUsername)) {
			choices.add(userName);
		}

		if (userNickname != null && userNickname.startsWith(proposedUsername)) {
			choices.add(userNickname);
		}

		event.replyChoices(choices.stream().map(name -> new Command.Choice(name, name)).toList()).queue();
	}

}
