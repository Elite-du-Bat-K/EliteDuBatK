package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.jetbrains.annotations.NotNull;

/** Transmet un formulaire envoyé à la commande qui l'a ouvert (identifiant {@code <commande>:…}). */
public class ModalEventListener extends AbstractEventListener {
	public ModalEventListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onModalInteraction(@NotNull ModalInteractionEvent event) {
		String commandName = event.getModalId().split(":", 2)[0];

		this.getBot()
				.getCommandManager()
				.getCommands()
				.stream()
				.filter(command -> command.getCommandInformation().getName().equals(commandName))
				.findFirst()
				.ifPresentOrElse(
						command -> command.onModal(event),
						() -> event.reply("Ce formulaire n'est plus actif.").setEphemeral(true).queue()
				);
	}
}
