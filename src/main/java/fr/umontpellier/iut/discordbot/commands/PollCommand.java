package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.repositories.PollChoiceRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollCommentRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollRoleRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollVoterRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PollCommand extends AbstractCommandWithAutocomplete {

	public PollCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData createData = new SubcommandData("create", "Créer un nouveau sondage")
				.addOption(OptionType.INTEGER, "duration_minutes", "Durée du sondage en minutes (optionnel)", false);

		SubcommandData closeData = new SubcommandData("close", "Fermer un sondage")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true);

		SubcommandData voteData = new SubcommandData("vote", "Voter à un sondage")
				.addOption(OptionType.INTEGER, "poll_id", "ID du sondage", true, true)
				.addOption(OptionType.INTEGER, "choice_index", "Sélectionne ton choix", true, true);

		SubcommandData resultsData = new SubcommandData("results", "Voir les résultats du sondage")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true)
				.addOption(OptionType.BOOLEAN, "public", "Rendre les résultats visibles par tous", false);

		SubcommandData exportResultsData = new SubcommandData("export", "Exporter les résultats du sondage (TXT)")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true);

		SubcommandData commentData = new SubcommandData("comment", "Ajouter un commentaire")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true)
				.addOption(OptionType.BOOLEAN, "anonymous", "Masquer ton identité", true);

		SubcommandData exportCommentsData = new SubcommandData("export_comments",
				"Exporter tous les commentaires (TSV)")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true);

		SubcommandData deleteData = new SubcommandData("delete", "Supprimer le sondage et toutes ses données")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true);

		SubcommandData viewData = new SubcommandData("info", "Voir les informations et métadonnées du sondage")
				.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true);

		SubcommandData publicKeyData = new SubcommandData("publickey",
				"Télécharger la clé publique du serveur pour vérifier les reçus");

		SubcommandData regenerateKeysData = new SubcommandData("regenerate_keys",
				"Générer une nouvelle paire de clés (Admin)");

		SubcommandGroupData restrictionGroup = new SubcommandGroupData("restriction", "Gérer les restrictions de vote")
				.addSubcommands(
						new SubcommandData("mode", "Définir le mode de restriction")
								.addOption(OptionType.STRING, "id", "ID du sondage", true, true)
								.addOption(OptionType.STRING, "type", "Mode : NONE, WHITELIST, BLACKLIST", true)
								.addOption(OptionType.BOOLEAN, "force", "Forcer le changement (supprime les rôles)",
										false),
						new SubcommandData("add_role", "Ajouter un rôle à la liste de restriction")
								.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true)
								.addOption(OptionType.ROLE, "role", "Rôle cible", true),
						new SubcommandData("remove_role", "Retirer un rôle de la liste de restriction")
								.addOption(OptionType.INTEGER, "id", "ID du sondage", true, true)
								.addOption(OptionType.ROLE, "role", "Rôle cible", true));

		return Commands.slash("poll", "Gérer les sondages")
				.addSubcommands(createData, closeData, voteData, resultsData, exportResultsData, commentData,
						exportCommentsData, deleteData, viewData, publicKeyData, regenerateKeysData)
				.addSubcommandGroups(restrictionGroup)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subName = event.getSubcommandName();
		String groupName = event.getSubcommandGroup();
		boolean hasSubName = subName != null;
		boolean hasGroupName = groupName != null;

		if (hasGroupName) {
			boolean isRestrictionGroup = groupName.equals("restriction");
			if (isRestrictionGroup) {
				boolean isMode = subName.equals("mode");
				boolean isAddRole = subName.equals("add_role");
				boolean isRemoveRole = subName.equals("remove_role");

				if (isMode) {
					this.handleRestrictionMode(event);
				} else if (isAddRole) {
					this.handleRestrictionAddRole(event);
				} else if (isRemoveRole) {
					this.handleRestrictionRemoveRole(event);
				} else {
					event.reply("Sous-commande de restriction inconnue.").setEphemeral(true).queue();
				}
			} else {
				event.reply("Groupe de sous-commandes inconnu.").setEphemeral(true).queue();
			}
		} else if (hasSubName) {
			boolean isCreate = subName.equals("create");
			boolean isClose = subName.equals("close");
			boolean isVote = subName.equals("vote");
			boolean isResults = subName.equals("results");
			boolean isExport = subName.equals("export");
			boolean isComment = subName.equals("comment");
			boolean isExportComments = subName.equals("export_comments");
			boolean isDelete = subName.equals("delete");
			boolean isView = subName.equals("info");
			boolean isPublicKey = subName.equals("publickey");
			boolean isRegenerateKeys = subName.equals("regenerate_keys");

			if (isCreate) {
				this.handleCreate(event);
			} else if (isClose) {
				this.handleClose(event);
			} else if (isVote) {
				this.handleVote(event);
			} else if (isResults) {
				this.handleResults(event);
			} else if (isExport) {
				this.handleExport(event);
			} else if (isComment) {
				this.handleComment(event);
			} else if (isExportComments) {
				this.handleExportComments(event);
			} else if (isDelete) {
				this.handleDelete(event);
			} else if (isView) {
				this.handleView(event);
			} else if (isPublicKey) {
				this.handlePublicKey(event);
			} else if (isRegenerateKeys) {
				this.handleRegenerateKeys(event);
			} else {
				event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
			}
		} else {
			event.reply("Aucune sous-commande fournie.").setEphemeral(true).queue();
		}
	}

	@Override
	public void onModal(ModalInteractionEvent event) {
		String modalId = event.getModalId();
		boolean isCreate = modalId.startsWith("poll_create|");
		boolean isComment = modalId.startsWith("poll_comment|");

		if (isCreate) {
			String[] parts = modalId.split("\\|");
			this.processCreateModal(event, parts[1]);
		} else if (isComment) {
			String[] parts = modalId.split("\\|");
			this.processCommentModal(event, parts[1], parts[2]);
		} else {
			event.reply("Action de formulaire inconnue.").setEphemeral(true).queue();
		}
	}

	@Override
	public void autocomplete(CommandAutoCompleteInteractionEvent event) {
		String focusedOption = event.getFocusedOption().getName();
		boolean isPollIdOption = focusedOption.equals("poll_id") || focusedOption.equals("id");
		boolean isChoiceIndexOption = focusedOption.equals("choice_index");

		List<Command.Choice> choices = new ArrayList<>();

		if (isPollIdOption) {
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			List<PollRepository.Poll> polls = pollRepo.findAll();

			for (PollRepository.Poll p : polls) {
				boolean isEligible = this.isEffectivelyOpen(p) || event.getSubcommandName().equals("results")
						|| event.getSubcommandName().equals("export")
						|| event.getSubcommandName().equals("export_comments")
						|| event.getSubcommandName().equals("delete") || event.getSubcommandName().equals("info");
				if (isEligible) {
					choices.add(new Command.Choice(p.getTitle(), p.getId()));
				}
			}
		} else if (isChoiceIndexOption) {
			OptionMapping pollIdMapping = event.getOption("poll_id");
			boolean hasPollId = pollIdMapping != null;

			if (hasPollId) {
				long pollId = pollIdMapping.getAsLong();
				PollChoiceRepository choiceRepo = this.getBot().getRepositories()
						.getRepository(PollChoiceRepository.class);
				List<PollChoiceRepository.PollChoice> pollChoices = choiceRepo.findByPollId(pollId);

				for (PollChoiceRepository.PollChoice c : pollChoices) {
					choices.add(new Command.Choice(c.getChoiceText(), c.getLocalIndex()));
				}
			}
		}

		List<Command.Choice> limitedChoices = new ArrayList<>();
		int count = 0;
		for (Command.Choice c : choices) {
			boolean underLimit = count < 25;
			if (underLimit) {
				limitedChoices.add(c);
				count = count + 1;
			}
		}
		event.replyChoices(limitedChoices).queue();
	}

	private boolean isEffectivelyOpen(PollRepository.Poll poll) {
		boolean isOpen = false;
		boolean isManuallyOpen = poll.isOpen();

		if (isManuallyOpen) {
			boolean hasClosesAt = poll.getClosesAt() != null;
			if (hasClosesAt) {
				long currentUtcTime = Instant.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
				boolean isPast = currentUtcTime > poll.getClosesAt();
				if (!isPast) {
					isOpen = true;
				}
			} else {
				isOpen = true;
			}
		}
		return isOpen;
	}

	private void handleCreate(SlashCommandInteractionEvent event) {
		OptionMapping durationOpt = event.getOption("duration_minutes");
		String durationStr = "-1";
		boolean hasDuration = durationOpt != null;

		if (hasDuration) {
			durationStr = String.valueOf(durationOpt.getAsLong());
		}

		TextInput titleInput = TextInput.create("title", TextInputStyle.SHORT)
				.setRequired(true)
				.build();

		TextInput descInput = TextInput.create("description", TextInputStyle.PARAGRAPH)
				.setRequired(true)
				.build();

		TextInput choicesInput = TextInput.create("choices", TextInputStyle.PARAGRAPH)
				.setPlaceholder("Exemple de choix:\nOui, tout à fait\nNon, pas du tout\nJe ne sais pas")
				.setRequired(true)
				.build();

		Modal modal = Modal.create("poll_create|" + durationStr, "Création de sondage")
				.addComponents(
						Label.of("Titre du sondage", titleInput),
						Label.of("Description du sondage", descInput),
						Label.of("Options (1 par ligne obligatoire, 2 min)", choicesInput))
				.build();

		event.replyModal(modal).queue();
	}

	private void processCreateModal(ModalInteractionEvent event, String durationStr) {
		String responseMessage = "Échec de la création du sondage.";
		String title = event.getValue("title").getAsString();
		String description = event.getValue("description").getAsString();
		String choicesRaw = event.getValue("choices").getAsString();

		Long closesAt = null;
		boolean hasDuration = !durationStr.equals("-1");

		if (hasDuration) {
			long durationMs = Long.parseLong(durationStr) * 60L * 1000L;
			closesAt = Instant.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli() + durationMs;
		}

		String[] choices = choicesRaw.split("\\r?\\n");
		boolean hasValidChoices = choices.length >= 2;

		if (hasValidChoices) {
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);

			PollRepository.Poll newPoll = new PollRepository.Poll(0, title, description, true, event.getUser().getId(),
					closesAt, "NONE");
			pollRepo.insert(newPoll);

			long pollId = 0L;
			List<PollRepository.Poll> allPolls = pollRepo.findAll();
			long latestId = 0L;

			for (PollRepository.Poll p : allPolls) {
				boolean isLater = p.getId() > latestId;
				if (isLater) {
					latestId = p.getId();
				}
			}
			pollId = latestId;

			long localIndex = 1L;
			for (String c : choices) {
				String cleanChoice = c.trim();
				boolean isNotEmpty = !cleanChoice.isEmpty();
				if (isNotEmpty) {
					choiceRepo.insert(new PollChoiceRepository.PollChoice(0, pollId, localIndex, cleanChoice, 0));
					localIndex = localIndex + 1L;
				}
			}
			responseMessage = "Sondage créé avec succès avec l'ID " + pollId;
		} else {
			responseMessage = "Tu dois fournir au moins deux choix séparés par un retour à la ligne.";
		}

		event.reply(responseMessage).queue();
	}

	private void handleComment(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		boolean anonymous = Objects.requireNonNull(event.getOption("anonymous")).getAsBoolean();

		TextInput textInput = TextInput.create("text", TextInputStyle.PARAGRAPH)
				.setRequired(true)
				.build();

		Modal modal = Modal.create("poll_comment|" + pollId + "|" + anonymous, "Ajouter un commentaire")
				.addComponents(Label.of("Texte du commentaire", textInput))
				.build();

		event.replyModal(modal).queue();
	}

	private void processCommentModal(ModalInteractionEvent event, String pollIdStr, String anonymousStr) {
		long pollId = Long.parseLong(pollIdStr);
		boolean anonymous = Boolean.parseBoolean(anonymousStr);
		String text = event.getValue("text").getAsString();
		String responseMessage = "Échec de l'ajout du commentaire.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			String userIdParam = "";
			if (!anonymous) {
				userIdParam = event.getUser().getId();
			} else {
				userIdParam = null;
			}

			PollCommentRepository commentRepo = this.getBot().getRepositories()
					.getRepository(PollCommentRepository.class);
			commentRepo.insert(new PollCommentRepository.PollComment(0, pollId, userIdParam, text, anonymous));
			responseMessage = "Commentaire ajouté avec succès.";
		} else {
			responseMessage = "Sondage introuvable.";
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleClose(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "Échec de l'opération de fermeture.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canClose = isCreator || isAdmin;
			if (canClose) {
				PollRepository.Poll updatedPoll = new PollRepository.Poll(p.getId(), p.getTitle(), p.getDescription(),
						false, p.getCreatorId(), p.getClosesAt(), p.getRestrictionMode());
				pollRepo.update(updatedPoll);
				responseMessage = "Le sondage " + pollId + " a été marqué comme fermé.";
			} else {
				responseMessage = "Tu n'as pas la permission de fermer ce sondage.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleDelete(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "Échec de la suppression.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canDelete = isCreator || isAdmin;
			if (canDelete) {
				pollRepo.deleteByPrimaryKey(pollId);
				this.getBot().getRepositories().getRepository(PollChoiceRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollCommentRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollVoterRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollRoleRepository.class).deleteByPollId(pollId);
				responseMessage = "Le sondage " + pollId
						+ " et toutes les données associées ont été complètement supprimés.";
			} else {
				responseMessage = "Tu n'as pas la permission de supprimer ce sondage.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleView(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "Échec de l'opération d'affichage.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			StringBuilder sb = new StringBuilder();
			sb.append("**Titre** : ").append(p.getTitle()).append("\n");
			sb.append("**Description** : ").append(p.getDescription()).append("\n");
			sb.append("**Statut** : ").append(this.isEffectivelyOpen(p) ? "Ouvert" : "Fermé").append("\n");

			boolean hasClosesAt = p.getClosesAt() != null;
			if (hasClosesAt) {
				long unixSeconds = p.getClosesAt() / 1000L;
				sb.append("**Fermeture (UTC)** : <t:").append(unixSeconds).append(":F>\n");
			}
			sb.append("**Mode de restriction** : ").append(p.getRestrictionMode()).append("\n");

			PollRoleRepository roleRepo = this.getBot().getRepositories().getRepository(PollRoleRepository.class);
			List<PollRoleRepository.PollRole> roles = roleRepo.findByPollId(pollId);
			boolean hasRoles = !roles.isEmpty();

			if (hasRoles) {
				sb.append("**Rôles cibles** : ");
				for (PollRoleRepository.PollRole pr : roles) {
					sb.append("<@&").append(pr.getRoleId()).append("> ");
				}
				sb.append("\n");
			}

			sb.append("\n**Choix** :\n");
			PollChoiceRepository choiceRepo = this.getBot().getRepositories()
					.getRepository(PollChoiceRepository.class);
			List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);

			for (PollChoiceRepository.PollChoice c : choices) {
				sb.append(c.getLocalIndex()).append(". ").append(c.getChoiceText()).append("\n");
			}
			responseMessage = sb.toString();
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionMode(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String modeInput = Objects.requireNonNull(event.getOption("type")).getAsString().toUpperCase();
		OptionMapping forceOpt = event.getOption("force");
		boolean force = forceOpt != null && forceOpt.getAsBoolean();
		String responseMessage = "Échec de la mise à jour de la restriction.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				boolean validMode = modeInput.equals("NONE") || modeInput.equals("WHITELIST")
						|| modeInput.equals("BLACKLIST");
				if (validMode) {
					boolean proceed = true;
					if (!p.getRestrictionMode().equals(modeInput) && !p.getRestrictionMode().equals("NONE")) {
						if (!force) {
							proceed = false;
							responseMessage = "Impossible de modifier le mode de restriction sans 'force: True', car cela supprimera les rôles existants.";
						} else {
							this.getBot().getRepositories().getRepository(PollRoleRepository.class)
									.deleteByPollId(pollId);
						}
					}

					if (proceed) {
						PollRepository.Poll updatedPoll = new PollRepository.Poll(p.getId(), p.getTitle(),
								p.getDescription(), p.isOpen(), p.getCreatorId(), p.getClosesAt(), modeInput);
						pollRepo.update(updatedPoll);
						responseMessage = "Mode de restriction du sondage mis à jour en " + modeInput;
					}
				} else {
					responseMessage = "Mode invalide. Utilise NONE, WHITELIST ou BLACKLIST.";
				}
			} else {
				responseMessage = "Tu n'as pas la permission de modifier ce sondage.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionAddRole(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		Role targetRole = Objects.requireNonNull(event.getOption("role")).getAsRole();
		String responseMessage = "Échec de l'ajout du rôle.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				boolean isNone = p.getRestrictionMode().equals("NONE");
				if (isNone) {
					responseMessage = "Impossible d'ajouter des rôles quand le mode de restriction est NONE. Change le mode d'abord.";
				} else {
					PollRoleRepository roleRepo = this.getBot().getRepositories()
							.getRepository(PollRoleRepository.class);
					roleRepo.insert(new PollRoleRepository.PollRole(0, pollId, targetRole.getId()));
					responseMessage = "Rôle ajouté avec succès à la liste " + p.getRestrictionMode() + " du sondage.";
				}
			} else {
				responseMessage = "Tu n'as pas la permission de modifier ce sondage.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionRemoveRole(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		Role targetRole = Objects.requireNonNull(event.getOption("role")).getAsRole();
		String responseMessage = "Échec de la suppression du rôle.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				PollRoleRepository roleRepo = this.getBot().getRepositories()
						.getRepository(PollRoleRepository.class);
				boolean deleted = roleRepo.deleteByPollIdAndRoleId(pollId, targetRole.getId());
				if (deleted) {
					responseMessage = "Rôle retiré avec succès de la liste de restriction du sondage.";
				} else {
					responseMessage = "Le rôle n'a pas été trouvé dans la liste de restriction.";
				}
			} else {
				responseMessage = "Tu n'as pas la permission de modifier ce sondage.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleVote(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("poll_id")).getAsLong();
		long choiceIndex = Objects.requireNonNull(event.getOption("choice_index")).getAsLong();
		String userId = event.getUser().getId();
		String responseMessage = "Échec de l'enregistrement du vote.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
		PollVoterRepository voterRepo = this.getBot().getRepositories().getRepository(PollVoterRepository.class);

		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPollPresent = pollOpt.isPresent();

		if (isPollPresent) {
			PollRepository.Poll poll = pollOpt.get();
			boolean isOpen = this.isEffectivelyOpen(poll);

			if (isOpen) {
				boolean canVote = false;
				String mode = poll.getRestrictionMode();
				boolean isNone = mode.equals("NONE");
				boolean isWhitelist = mode.equals("WHITELIST");
				boolean isBlacklist = mode.equals("BLACKLIST");

				Member member = event.getMember();
				boolean hasMember = member != null;

				if (isNone) {
					canVote = true;
				} else if (hasMember) {
					PollRoleRepository roleRepo = this.getBot().getRepositories()
							.getRepository(PollRoleRepository.class);
					List<PollRoleRepository.PollRole> restrictedRoles = roleRepo.findByPollId(pollId);

					if (isWhitelist) {
						boolean hasWhitelistedRole = false;
						for (PollRoleRepository.PollRole pr : restrictedRoles) {
							boolean hasRole = member.getRoles().stream()
									.anyMatch(r -> r.getId().equals(pr.getRoleId()));
							if (hasRole) {
								hasWhitelistedRole = true;
							}
						}
						if (hasWhitelistedRole) {
							canVote = true;
						} else {
							responseMessage = "Tu n'as pas le rôle requis (liste blanche) pour voter à ce sondage.";
						}
					} else if (isBlacklist) {
						boolean hasBlacklistedRole = false;
						for (PollRoleRepository.PollRole pr : restrictedRoles) {
							boolean hasRole = member.getRoles().stream()
									.anyMatch(r -> r.getId().equals(pr.getRoleId()));
							if (hasRole) {
								hasBlacklistedRole = true;
							}
						}
						if (!hasBlacklistedRole) {
							canVote = true;
						} else {
							responseMessage = "Ton rôle est sur liste noire pour ce sondage.";
						}
					}
				} else {
					responseMessage = "Tu dois exécuter cette commande dans un serveur pour respecter les restrictions de rôle.";
				}

				if (canVote) {
					Optional<PollChoiceRepository.PollChoice> choiceOpt = choiceRepo.findByPollIdAndLocalIndex(pollId,
							choiceIndex);
					boolean isChoicePresent = choiceOpt.isPresent();

					if (isChoicePresent) {
						boolean voteRegistered = voterRepo.recordVoteAttempt(pollId, userId);
						if (voteRegistered) {
							choiceRepo.incrementVote(pollId, choiceIndex);
							responseMessage = "Vote enregistré avec succès.";

							String receiptPayload = String.format("POLL_RECEIPT_V1|%d|%s", pollId, userId);
							String signature = this.getBot().getCryptoReceiptService().signPayload(receiptPayload);
							boolean hasSignature = !signature.isEmpty();

							if (hasSignature) {
								byte[] sigBytes = signature.getBytes(StandardCharsets.UTF_8);
								FileUpload sigUpload = FileUpload.fromData(sigBytes, "receipt_" + pollId + ".sig");
								event.getUser().openPrivateChannel().queue(
										channel -> channel.sendMessage(
												"Ton vote a bien été pris en compte pour le sondage " + pollId + ".\n"
														+ "Voici ton reçu cryptographique.\n"
														+ "Contenu du message signé : `" + receiptPayload + "`")
												.addFiles(sigUpload).queue(
														success -> {
														},
														error -> {
														}),
										error -> {
										});
							}
						} else {
							responseMessage = "Tu as déjà voté à ce sondage.";
						}
					} else {
						responseMessage = "Choix introuvable.";
					}
				}
			} else {
				responseMessage = "Ce sondage est fermé.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleResults(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		OptionMapping publicOpt = event.getOption("public");
		boolean isPublic = false;
		boolean hasPublicOpt = publicOpt != null;

		if (hasPublicOpt) {
			isPublic = publicOpt.getAsBoolean();
		}

		String responseMessage = "Échec de la récupération des résultats.";
		boolean ephemeralResponse = true;

		PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);

		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll poll = pollOpt.get();
			boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;

			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;

			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean hasPermission = isCreator || isAdmin;
			if (hasPermission) {
				List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);
				long totalSum = 0L;
				for (PollChoiceRepository.PollChoice c : choices) {
					totalSum = totalSum + c.getVotesCount();
				}

				StringBuilder sb = new StringBuilder();
				sb.append("**").append(poll.getTitle()).append("**\n");
				sb.append("Total des votes : ").append(totalSum).append("\n\n");

				for (PollChoiceRepository.PollChoice c : choices) {
					sb.append(c.getLocalIndex()).append(" - ").append(c.getChoiceText()).append(" : ")
							.append(c.getVotesCount()).append(" votes\n");
				}
				responseMessage = sb.toString();
				if (isPublic) {
					ephemeralResponse = false;
				}
			} else {
				responseMessage = "Tu n'as pas la permission de voir ces résultats.";
			}
		} else {
			responseMessage = "Sondage introuvable.";
		}
		event.reply(responseMessage).setEphemeral(ephemeralResponse).queue();
	}

	private void handleExport(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		event.deferReply(true).queue();

		java.util.concurrent.CompletableFuture.runAsync(() -> {
			String errorMessage = "Échec de l'exportation.";
			boolean success = false;
			FileUpload uploadData = null;

			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
			PollVoterRepository voterRepo = this.getBot().getRepositories().getRepository(PollVoterRepository.class);
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);

			Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
			boolean isPresent = pollOpt.isPresent();

			if (isPresent) {
				PollRepository.Poll poll = pollOpt.get();
				boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
				boolean isAdmin = false;

				Member eventMember = event.getMember();
				boolean hasMember = eventMember != null;

				if (hasMember) {
					isAdmin = this.isAdmin(eventMember);
				}

				boolean hasPermission = isCreator || isAdmin;
				if (hasPermission) {
					List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);
					List<PollVoterRepository.PollVoter> voters = voterRepo.findByPollId(pollId);

					long totalSum = 0L;
					for (PollChoiceRepository.PollChoice c : choices) {
						totalSum = totalSum + c.getVotesCount();
					}

					StringBuilder txtBuilder = new StringBuilder();
					txtBuilder.append("ID du sondage : ").append(pollId).append("\n");
					txtBuilder.append("Total des votes : ").append(totalSum).append("\n\n");
					txtBuilder.append("Résultats :\n");

					for (PollChoiceRepository.PollChoice c : choices) {
						txtBuilder.append("- ").append(c.getChoiceText()).append(" : ").append(c.getVotesCount())
								.append(" votes\n");
					}

					txtBuilder.append("\nUtilisateurs ayant voté :\n");
					Guild guild = event.getGuild();
					boolean hasGuild = guild != null;

					for (PollVoterRepository.PollVoter v : voters) {
						String identity = v.getUserId();
						if (hasGuild) {
							Member m = guild.getMemberById(v.getUserId());
							boolean isMemberPresent = m != null;
							if (isMemberPresent) {
								identity = m.getEffectiveName();
							}
						}

						boolean identityIsStillId = identity.equals(v.getUserId());
						if (identityIsStillId && hasGuild) {
							try {
								identity = guild.retrieveMemberById(v.getUserId()).complete().getEffectiveName();
							} catch (Exception ignored) {
							}
						}

						boolean identityIsStillIdAfterMember = identity.equals(v.getUserId());
						if (identityIsStillIdAfterMember) {
							try {
								identity = event.getJDA().retrieveUserById(v.getUserId()).complete().getName();
							} catch (Exception ignored) {
							}
						}
						txtBuilder.append(identity).append("\n");
					}

					byte[] bytes = txtBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_export.txt");
					success = true;
				} else {
					errorMessage = "Tu n'as pas la permission d'exporter ce sondage.";
				}
			} else {
				errorMessage = "Sondage introuvable.";
			}

			if (success) {
				event.getHook().sendFiles(uploadData).queue();
			} else {
				event.getHook().sendMessage(errorMessage).queue();
			}
		});
	}

	private void handleExportComments(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		event.deferReply(true).queue();

		java.util.concurrent.CompletableFuture.runAsync(() -> {
			String errorMessage = "Échec de l'exportation des commentaires.";
			boolean success = false;
			FileUpload uploadData = null;

			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
			boolean isPresent = pollOpt.isPresent();

			if (isPresent) {
				PollRepository.Poll poll = pollOpt.get();
				boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
				boolean isAdmin = false;

				Member eventMember = event.getMember();
				boolean hasMember = eventMember != null;

				if (hasMember) {
					isAdmin = this.isAdmin(eventMember);
				}

				boolean hasPermission = isCreator || isAdmin;
				if (hasPermission) {
					PollCommentRepository commentRepo = this.getBot().getRepositories()
							.getRepository(PollCommentRepository.class);
					List<PollCommentRepository.PollComment> comments = commentRepo.findByPollId(pollId);
					Guild guild = event.getGuild();
					boolean hasGuild = guild != null;

					StringBuilder tsvBuilder = new StringBuilder();
					tsvBuilder.append("Utilisateur\tCommentaire\n");

					for (PollCommentRepository.PollComment c : comments) {
						String identity = "Anonyme";
						boolean isNotAnonymous = !c.isAnonymous() && c.getUserId() != null;

						if (isNotAnonymous) {
							identity = c.getUserId();
							if (hasGuild) {
								Member m = guild.getMemberById(c.getUserId());
								boolean isMemberPresent = m != null;
								if (isMemberPresent) {
									identity = m.getEffectiveName();
								}
							}

							boolean identityIsStillId = identity.equals(c.getUserId());
							if (identityIsStillId && hasGuild) {
								try {
									identity = guild.retrieveMemberById(c.getUserId()).complete()
											.getEffectiveName();
								} catch (Exception ignored) {
								}
							}

							boolean identityIsStillIdAfterMember = identity.equals(c.getUserId());
							if (identityIsStillIdAfterMember) {
								try {
									identity = event.getJDA().retrieveUserById(c.getUserId()).complete().getName();
								} catch (Exception ignored) {
								}
							}
						}

						String safeComment = c.getCommentText().replace("\n", " ").replace("\t", " ");
						String safeIdentity = identity.replace("\n", " ").replace("\t", " ");
						tsvBuilder.append(safeIdentity).append("\t").append(safeComment).append("\n");
					}

					byte[] bytes = tsvBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_comments.tsv");
					success = true;
				} else {
					errorMessage = "Tu n'as pas la permission d'exporter les commentaires de ce sondage.";
				}
			} else {
				errorMessage = "Sondage introuvable.";
			}

			if (success) {
				event.getHook().sendFiles(uploadData).queue();
			} else {
				event.getHook().sendMessage(errorMessage).queue();
			}
		});
	}

	private void handlePublicKey(SlashCommandInteractionEvent event) {
		event.deferReply(true).queue();
		boolean isSuccess = false;
		String errorMessage = "Échec de la récupération de la clé publique.";

		try {
			FileUpload keyUpload = FileUpload.fromData(this.getBot().getCryptoReceiptService().getPublicKeyFile(),
					"public.pem");
			event.getHook().sendMessage("Voici la clé publique du serveur (format Ed25519 X.509 PEM) :")
					.addFiles(keyUpload)
					.queue();
			isSuccess = true;
		} catch (Exception e) {
			isSuccess = false;
		}

		if (!isSuccess) {
			event.getHook().sendMessage(errorMessage).queue();
		}
	}

	private void handleRegenerateKeys(SlashCommandInteractionEvent event) {
		String responseMessage = "Opération impossible : membre introuvable.";
		Member member = event.getMember();
		boolean hasMember = member != null;

		if (hasMember) {
			boolean isAdmin = this.isAdmin(member);
			if (isAdmin) {
				boolean success = false;
				try {
					this.getBot().getCryptoReceiptService().forceGenerateKeys();
					success = true;
				} catch (Exception ignored) {
					success = false;
				}

				if (success) {
					responseMessage = "Nouvelle paire de clés générée et sauvegardée avec succès.";
				}

				if (!success) {
					responseMessage = "Erreur lors de la génération ou de la sauvegarde des clés.";
				}
			}

			if (!isAdmin) {
				responseMessage = "Tu n'as pas la permission d'exécuter cette commande.";
			}
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}
}