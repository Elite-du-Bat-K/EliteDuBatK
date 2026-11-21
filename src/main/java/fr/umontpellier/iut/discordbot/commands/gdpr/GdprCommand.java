package fr.umontpellier.iut.discordbot.commands.gdpr;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.GdprDeletionRequest;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.services.GdprService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GdprCommand extends AbstractCommand {

	public GdprCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData deleteData = new SubcommandData("delete", "Demander l'effacement de tes données personnelles");
		SubcommandData statusData = new SubcommandData("status", "Voir l'état de ta demande d'effacement");
		SubcommandData cancelData = new SubcommandData("cancel", "Annuler ta demande d'effacement en cours");

		return Commands.slash("rgpd", "Gérer tes données personnelles (RGPD)")
				.addSubcommands(deleteData, statusData, cancelData);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subcommand = event.getSubcommandName();
		String reply = "Sous-commande inconnue.";
		GdprService gdprService = this.getBot().getGdprService();
		String userId = event.getUser().getId();

		boolean isDelete = "delete".equals(subcommand);
		boolean isStatus = "status".equals(subcommand);
		boolean isCancel = "cancel".equals(subcommand);

		if (isDelete) {
			Optional<GdprDeletionRequest> createdOpt = gdprService.createDeletionRequest(userId);
			reply = "Tu as déjà une demande d'effacement en cours.";
			if (createdOpt.isPresent()) {
				GdprDeletionRequest req = createdOpt.get();
				reply = String.format(
						"Ta demande d'effacement (`#%d`) a bien été enregistrée.\n"
								+ "L'équipe d'administration a été notifiée. Sauf rétention motivée, tes données seront automatiquement supprimées le <t:%d:F> (délai de 48h).",
						req.getId(), req.getExecuteAt() / 1000L);
			}
		}

		if (isStatus) {
			Optional<GdprDeletionRequest> pendingOpt = gdprService.getPendingRequest(userId);
			reply = "Tu n'as aucune demande d'effacement en cours.";
			if (pendingOpt.isPresent()) {
				GdprDeletionRequest req = pendingOpt.get();
				reply = String.format(
						"Demande `#%d` en attente de vérification.\nExécution automatique prévue le <t:%d:F>.",
						req.getId(), req.getExecuteAt() / 1000L);
			}
		}

		if (isCancel) {
			boolean cancelled = gdprService.cancelPendingRequest(userId);
			reply = "Aucune demande d'effacement en cours à annuler.";
			if (cancelled) {
				reply = "Ta demande d'effacement a bien été annulée.";
			}
		}

		event.reply(reply).setEphemeral(true).queue();
	}
}