package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.services.ServiceStatus;
import fr.umontpellier.iut.discordbot.services.ServiceStatusMonitor;
import fr.umontpellier.iut.discordbot.services.TrackedStatus;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /status} : est-ce que Moodle, GitLab… répondent, et depuis quand ? Le résultat vient des vérifications
 * régulières de {@link ServiceStatusMonitor}, faites depuis le serveur du bot : on sait ainsi si un problème vient du
 * service ou de sa propre connexion.
 */
public class StatusCommand extends AbstractCommand {

	public StatusCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("status", "Voir si Moodle, GitLab… sont accessibles, et depuis quand");
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		ServiceStatusMonitor monitor = getBot().getStatusMonitor();
		List<TrackedStatus> statuses = monitor.getStatuses();
		String reply = "⏳ Première vérification en cours, réessaie dans quelques secondes.";

		if (!monitor.hasTargets()) {
			reply = "Aucun service n'est configuré.";
		}

		if (!statuses.isEmpty()) {
			reply = formatReport(statuses);
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	static String formatReport(List<TrackedStatus> statuses) {
		return "**État des services** (vu depuis le serveur du bot)\n"
				+ statuses.stream().map(StatusCommand::formatLine).collect(Collectors.joining("\n"))
				+ "\n-# Vérifié " + timestamp(statuses.getFirst().checkedAt())
				+ ". Si c'est vert ici mais que ça ne marche pas chez toi, le problème vient sans doute de ta connexion.";
	}

	static String formatLine(TrackedStatus tracked) {
		ServiceStatus status = tracked.latest();
		StatusTarget target = status.target();
		String publicUrl = target.getPublicUrl();
		boolean hasLink = publicUrl != null && !publicUrl.isBlank();
		String name = "**" + target.getDisplayName() + "**";

		if (hasLink) {
			name = "[" + target.getDisplayName() + "](<" + publicUrl + ">)";
		}

		String since = (tracked.sinceStartup() ? " depuis au moins " : " depuis ") + timestamp(tracked.since());

		return switch (status.state()) {
			case UP -> "🟢 " + name + " : en ligne" + since + " (HTTP " + status.httpCode() + ", " + status.latencyMillis() + " ms)";
			case DOWN -> "🔴 " + name + " : en panne" + since + " (HTTP " + status.httpCode() + ")";
			case UNREACHABLE -> "🔴 " + name + " : injoignable" + since + " (pas de réponse)";
			case INVALID_URL -> "⚠️ " + name + " : URL invalide dans la config";
		};
	}

	/** Affiché par Discord en relatif (« il y a 2 heures »), dans la langue de chacun. */
	private static String timestamp(Instant instant) {
		return "<t:" + instant.getEpochSecond() + ":R>";
	}
}
