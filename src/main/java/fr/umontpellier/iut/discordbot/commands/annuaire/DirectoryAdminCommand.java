package fr.umontpellier.iut.discordbot.commands.annuaire;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryPendingChange;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.services.DirectoryService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DirectoryAdminCommand extends AbstractCommand {
	public DirectoryAdminCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData listData = new SubcommandData("list", "Lister les modifications d'annuaire en attente");

		SubcommandData approveData = new SubcommandData("approve", "Valider une demande de modification d'annuaire")
				.addOption(OptionType.INTEGER, "id", "ID de la demande", true);

		SubcommandData rejectData = new SubcommandData("reject", "Refuser une demande de modification d'annuaire")
				.addOption(OptionType.INTEGER, "id", "ID de la demande", true)
				.addOption(OptionType.STRING, "motif", "Motif du refus (optionnel)", false);

		return Commands.slash("annuaire-admin", "Administration des demandes de modification de l'annuaire")
				.addSubcommands(listData, approveData, rejectData)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Member member = event.getMember();
		boolean isAuthorized = member != null && this.isAdmin(member);
		String reply = "Tu n'as pas la permission d'exécuter cette commande.";

		if (isAuthorized) {
			String subcommand = event.getSubcommandName();
			boolean isList = "list".equals(subcommand);
			boolean isApprove = "approve".equals(subcommand);
			boolean isReject = "reject".equals(subcommand);
			reply = "Sous-commande inconnue.";

			if (isList) {
				reply = this.formatPendingList(this.getBot().getDirectoryService().getPendingChanges());
			}

			if (isApprove && event.getOption("id") != null) {
				long id = event.getOption("id").getAsLong();
				DirectoryService.MutationResult result = this.getBot().getDirectoryService()
						.approveChange(id, event.getUser().getId());
				reply = result.message();
			}

			if (isReject && event.getOption("id") != null) {
				long id = event.getOption("id").getAsLong();
				OptionMapping motifOpt = event.getOption("motif");
				String motif = motifOpt != null ? motifOpt.getAsString() : null;
				DirectoryService.MutationResult result = this.getBot().getDirectoryService()
						.rejectChange(id, event.getUser().getId(), motif);
				reply = result.message();
			}
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	private String formatPendingList(List<DirectoryPendingChange> pendingChanges) {
		String output = "Aucune demande de modification d'annuaire en attente.";

		if (!pendingChanges.isEmpty()) {
			StringBuilder sb = new StringBuilder("**Demandes d'annuaire en attente :**\n");

			for (DirectoryPendingChange change : pendingChanges) {
				sb.append("- `#").append(change.getId()).append("` • **").append(change.getOperation())
						.append("** • ").append(change.getLastName());

				if (change.getFirstName() != null && !change.getFirstName().isEmpty()) {
					sb.append(" ").append(change.getFirstName());
				}

				sb.append(" (`").append(change.getEmail()).append("`) • par <@")
						.append(change.getRequesterId()).append("> • <t:")
						.append(change.getRequestedAt() / 1000L).append(":R>\n");
			}
			output = sb.toString();
		}

		return output;
	}
}