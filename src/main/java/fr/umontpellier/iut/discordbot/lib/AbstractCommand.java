package fr.umontpellier.iut.discordbot.lib;

import fr.umontpellier.iut.discordbot.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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

	protected boolean isAdmin(Member member) {
		String adminRoleId = getBot().getConfig().get().getAdminRole();
		if (adminRoleId == null || adminRoleId.isBlank()) return false;
		return member.getRoles().stream().anyMatch(r -> r.getId().equals(adminRoleId));
	}
}
