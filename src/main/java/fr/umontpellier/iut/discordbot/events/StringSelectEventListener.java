package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;

/** Transmet un choix dans un menu déroulant à la commande qui l'a envoyé (identifiant {@code <commande>:…}). */
public class StringSelectEventListener extends AbstractEventListener {
	public StringSelectEventListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
		String commandName = event.getComponentId().split(":", 2)[0];

		this.getBot()
				.getCommandManager()
				.getCommands()
				.stream()
				.filter(command -> command.getCommandInformation().getName().equals(commandName))
				.findFirst()
				.ifPresentOrElse(
						command -> command.onStringSelect(event),
						() -> event.reply("Ce menu n'est plus actif.").setEphemeral(true).queue()
				);
	}
}
