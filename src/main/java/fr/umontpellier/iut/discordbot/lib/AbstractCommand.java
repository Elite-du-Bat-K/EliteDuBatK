package fr.umontpellier.iut.discordbot.lib;

import fr.umontpellier.iut.discordbot.Bot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public abstract class AbstractCommand extends SharedBot {

	protected final Logger logger;

	public AbstractCommand(Bot bot) {
		super(bot);
		this.logger = LoggerFactory.getLogger(this.getClass());

		logger.info("Command initialized !");
	}

	public boolean hasAutocomplete() {
		return false;
	}

	public Optional<AbstractCommandWithAutocomplete> asAutocompleteCommand() {
		return Optional.empty();
	}

	public abstract @NotNull SlashCommandData getCommandInformation();
	public abstract void execute(SlashCommandInteractionEvent event);

	/**
	 * Un bouton envoyé par cette commande. Il lui revient quand son identifiant commence par
	 * {@code <nom de la commande>:}.
	 */
	public void onButton(ButtonInteractionEvent event) {
		event.reply("Ce bouton n'est plus actif.").setEphemeral(true).queue();
	}

	/**
	 * Un formulaire (modal) ouvert par cette commande. Il lui revient quand son identifiant commence par
	 * {@code <nom de la commande>:}.
	 */
	public void onModal(ModalInteractionEvent event) {
		event.reply("Ce formulaire n'est plus actif.").setEphemeral(true).queue();
	}

	/**
	 *
	 * @param event The event from which we want to get the member. It should be an instance of {@link Interaction}
	 * @return An {@link Optional} of the {@link Member} if it exists, else an empty {@link Optional}
	 */
	protected Optional<Member> getMember(Interaction event) {
		if (event.getMember() == null) {
			logger.warn("Impossible de récupérer le membre pour l'utilisateur {}", event.getUser().getId());
			return Optional.empty();
		}
		return Optional.of(event.getMember());
	}

	protected Optional<Guild> getGuild(Interaction event) {
		if (event.getGuild() == null) {
			logger.warn("Impossible de récupérer la guilde {}", event.getContext());
			return Optional.empty();
		}
		return Optional.of(event.getGuild());
	}

	/**
	 * Un membre est admin s'il a le rôle admin de la config, la permission Administrateur, ou s'il est propriétaire du serveur.
	 */
	protected boolean isAdmin(Member member) {
		if (member.hasPermission(Permission.ADMINISTRATOR)) return true;

		String adminRoleId = getBot().getConfig().get().getAdminRole();
		if (adminRoleId == null || adminRoleId.isBlank()) return false;
		return member.getRoles().stream().anyMatch(r -> r.getId().equals(adminRoleId));
	}

}
