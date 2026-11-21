package fr.umontpellier.iut.discordbot.commands.gdpr;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.CachedMessageData;
import fr.umontpellier.iut.discordbot.database.dataobjects.GdprDeletionRequest;
import fr.umontpellier.iut.discordbot.database.dataobjects.PreservedMessage;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.services.GdprService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GdprAdminCommand extends AbstractCommand {
	public GdprAdminCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData listData = new SubcommandData("list", "Lister les demandes d'effacement en attente");

		SubcommandData reviewData = new SubcommandData("review", "Exporter les messages en cache liés à une demande")
				.addOption(OptionType.INTEGER, "request_id", "ID de la demande RGPD", true);

		SubcommandData preserveData = new SubcommandData("preserve", "Isoler un ou plusieurs messages comme preuves")
				.addOption(OptionType.STRING, "message_ids", "ID(s) des messages (séparés par espaces ou virgules)",
						true)
				.addOption(OptionType.STRING, "reason", "Motif de la conservation", true);

		SubcommandData unpreserveData = new SubcommandData("unpreserve",
				"Retirer la protection d'un ou plusieurs messages")
				.addOption(OptionType.STRING, "message_ids", "ID(s) des messages (séparés par espaces ou virgules)",
						true);

		SubcommandData approveData = new SubcommandData("approve", "Exécuter immédiatement une demande d'effacement")
				.addOption(OptionType.INTEGER, "request_id", "ID de la demande RGPD", true)
				.addOption(OptionType.STRING, "motif", "Motif en cas de rétention partielle (optionnel)", false);

		return Commands.slash("rgpd-admin", "Administration des demandes RGPD et conservation de preuves")
				.addSubcommands(listData, reviewData, preserveData, unpreserveData, approveData)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Member member = event.getMember();
		boolean isAuthorized = member != null && this.isAdmin(member);

		if (!isAuthorized) {
			event.reply("Tu n'as pas la permission d'exécuter cette commande.").setEphemeral(true).queue();
		}

		if (isAuthorized) {
			String subcommand = event.getSubcommandName();
			boolean isList = "list".equals(subcommand);
			boolean isReview = "review".equals(subcommand);
			boolean isPreserve = "preserve".equals(subcommand);
			boolean isUnpreserve = "unpreserve".equals(subcommand);
			boolean isApprove = "approve".equals(subcommand);

			if (isList) {
				this.handleList(event);
			}
			if (isReview) {
				this.handleReview(event);
			}
			if (isPreserve) {
				this.handlePreserve(event);
			}
			if (isUnpreserve) {
				this.handleUnpreserve(event);
			}
			if (isApprove) {
				this.handleApprove(event);
			}
		}
	}

	private void handleList(SlashCommandInteractionEvent event) {
		List<GdprDeletionRequest> pending = this.getBot().getGdprService().getAllPendingRequests();
		String reply = "Aucune demande d'effacement en attente.";

		if (!pending.isEmpty()) {
			StringBuilder sb = new StringBuilder("**Demandes RGPD en attente :**\n");
			for (GdprDeletionRequest req : pending) {
				sb.append(String.format("- `#%d` • <@%s> (`%s`) • Exécution : <t:%d:R>\n",
						req.getId(), req.getUserId(), req.getUserId(), req.getExecuteAt() / 1000L));
			}
			reply = sb.toString();
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	private void handleReview(SlashCommandInteractionEvent event) {
		long requestId = event.getOption("request_id").getAsLong();
		GdprService gdprService = this.getBot().getGdprService();
		Optional<GdprDeletionRequest> reqOpt = gdprService.findRequestById(requestId);
		boolean hasRequest = reqOpt.isPresent();

		if (!hasRequest) {
			event.reply("Demande introuvable.").setEphemeral(true).queue();
		}

		if (hasRequest) {
			GdprDeletionRequest req = reqOpt.get();
			long userId = Long.parseLong(req.getUserId());
			List<CachedMessageData> cached = gdprService.getUserCachedMessages(userId);
			List<PreservedMessage> preserved = gdprService.getUserPreservedMessages(userId);
			Set<Long> preservedIds = new HashSet<>();

			for (PreservedMessage pm : preserved) {
				preservedIds.add(pm.getMessageId());
			}

			StringBuilder sb = new StringBuilder();
			sb.append("Revue RGPD — Demande #").append(req.getId())
					.append(" | Utilisateur : ").append(req.getUserId()).append("\n");
			sb.append("Messages en cache : ").append(cached.size())
					.append(" | Déjà conservés : ").append(preserved.size()).append("\n\n");

			for (CachedMessageData msg : cached) {
				String tag = preservedIds.contains(msg.getMessageId()) ? "[CONSERVÉ]" : "[CACHE]";
				sb.append(String.format("%s ID:%d | Salon:%d | %s\n%s\n---\n",
						tag, msg.getMessageId(), msg.getChannelId(),
						Instant.ofEpochMilli(msg.getTimestamp()), msg.getContent()));
			}

			byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
			FileUpload upload = FileUpload.fromData(bytes, "rgpd_review_" + req.getId() + ".txt");
			event.reply("Export des messages pour la demande `#" + req.getId() + "` :")
					.addFiles(upload)
					.setEphemeral(true)
					.queue();
		}
	}

	private void handlePreserve(SlashCommandInteractionEvent event) {
		String rawIds = event.getOption("message_ids").getAsString();
		String reason = event.getOption("reason").getAsString();
		List<Long> parsedIds = this.parseMessageIds(rawIds);
		String reply = "Aucun ID de message valide fourni.";

		if (!parsedIds.isEmpty()) {
			GdprService.BatchPreserveResult result = this.getBot().getGdprService()
					.preserveMessages(parsedIds, event.getUser().getId(), reason);
			reply = String.format(
					"Résultat de la conservation :\n- Conservés : %s\n- Déjà conservés : %s\n- Introuvables dans le cache : %s",
					result.preserved(), result.alreadyPreserved(), result.notFound());
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	private void handleUnpreserve(SlashCommandInteractionEvent event) {
		String rawIds = event.getOption("message_ids").getAsString();
		List<Long> parsedIds = this.parseMessageIds(rawIds);
		String reply = "Aucun ID de message valide fourni.";

		if (!parsedIds.isEmpty()) {
			GdprService.BatchUnpreserveResult result = this.getBot().getGdprService().unpreserveMessages(parsedIds);
			reply = String.format(
					"Résultat du retrait :\n- Retirés : %s\n- Introuvables : %s",
					result.removed(), result.notFound());
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	private void handleApprove(SlashCommandInteractionEvent event) {
		long requestId = event.getOption("request_id").getAsLong();
		OptionMapping motifOpt = event.getOption("motif");
		String motif = motifOpt != null ? motifOpt.getAsString() : null;
		boolean approved = this.getBot().getGdprService().approveRequest(requestId, event.getUser().getId(), motif);
		String reply = "Impossible d'approuver cette demande (introuvable ou déjà traitée).";

		if (approved) {
			reply = "Demande `#" + requestId + "` exécutée avec succès.";
		}

		event.reply(reply).setEphemeral(true).queue();
	}

	private List<Long> parseMessageIds(String rawIds) {
		List<Long> ids = new ArrayList<>();
		boolean hasInput = rawIds != null && !rawIds.isBlank();

		if (hasInput) {
			String[] tokens = rawIds.trim().split("[,;\\s]+");
			for (String token : tokens) {
				try {
					ids.add(Long.parseLong(token));
				} catch (NumberFormatException ignored) {
				}
			}
		}

		return ids;
	}
}