package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class SlashCommandEventListener extends AbstractEventListener {
	public SlashCommandEventListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		AbstractCommand targetCommand = null;
		boolean isCommandFound = false;

		for (AbstractCommand command : this.getBot().getCommandManager().getCommands()) {
			boolean matchesName = command.getCommandInformation().getName().equals(event.getName());
			if (matchesName) {
				targetCommand = command;
				isCommandFound = true;
			}
		}

		if (isCommandFound) {
			targetCommand.execute(event);
		}

		if (!isCommandFound) {
			event.reply("Commande inconnue...").setEphemeral(true).queue();
		}
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {}
}