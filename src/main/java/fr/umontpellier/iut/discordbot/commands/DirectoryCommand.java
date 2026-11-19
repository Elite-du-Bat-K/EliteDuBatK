package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryCategory;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import fr.umontpellier.iut.discordbot.database.repositories.DirectoryRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;
import java.util.Optional;

public class DirectoryCommand extends AbstractCommand {
	private static final int PAGE_SIZE = 10;

	public DirectoryCommand(Bot bot) {
		super(bot);
	}

	@Override
	public SlashCommandData getCommandInformation() {
		OptionData categoryOption = new OptionData(OptionType.STRING, "categorie", "Catégorie de l'entrée", true)
				.addChoice(DirectoryCategory.PROF.getDisplayName(), DirectoryCategory.PROF.name())
				.addChoice(DirectoryCategory.SERVICE.getDisplayName(), DirectoryCategory.SERVICE.name());

		SubcommandData addData = new SubcommandData("ajouter", "Ajouter une entrée")
				.addOptions(categoryOption)
				.addOption(OptionType.USER, "utilisateur", "Associer un utilisateur Discord", false);

		SubcommandData editData = new SubcommandData("modifier", "Modifier une entrée")
				.addOption(OptionType.INTEGER, "id", "ID de l'entrée", true);

		SubcommandData searchData = new SubcommandData("chercher", "Chercher parmi les entrées")
				.addOption(OptionType.STRING, "recherche", "Nom ou catégorie", true);

		SubcommandData listData = new SubcommandData("lister", "Lister toutes les entrées")
				.addOption(OptionType.INTEGER, "page", "Numéro de page", false);

		SubcommandData deleteData = new SubcommandData("supprimer", "Supprimer une entrée")
				.addOption(OptionType.INTEGER, "id", "ID de l'entrée", true);

		SlashCommandData commandData = Commands.slash("annuaire", "Gérer l'annuaire")
				.addSubcommands(addData, editData, searchData, listData, deleteData);

		return commandData;
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subcommand = "";
		boolean isMutationCommand = false;
		boolean hasPermission = false;
		boolean canExecute = false;
		String errorMessage = "Vous n'avez pas la permission de modifier l'annuaire.";
		boolean hasSubcommand = false;

		subcommand = event.getSubcommandName();
		hasSubcommand = (subcommand != null);

		if (hasSubcommand) {
			isMutationCommand = ("ajouter".equals(subcommand) || "modifier".equals(subcommand)
					|| "supprimer".equals(subcommand));
			hasPermission = this.checkMutationPermission(event.getMember());

			canExecute = (!isMutationCommand || hasPermission);

			if (canExecute) {
				this.routeSubcommand(event, subcommand);
			}
		}

		if (!canExecute) {
			event.reply(errorMessage).setEphemeral(true).queue();
		}
	}

	@Override
	public void onModal(ModalInteractionEvent event) {
		String modalId = "";
		boolean isAdd = false;
		boolean isEdit = false;
		boolean isDirectoryModal = false;
		boolean hasPermission = false;
		String errorMessage = "Vous n'avez pas la permission de modifier l'annuaire.";

		modalId = event.getModalId();
		isAdd = modalId.startsWith("annuaire:add|");
		isEdit = modalId.startsWith("annuaire:edit|");
		isDirectoryModal = (isAdd || isEdit);

		if (isDirectoryModal) {
			hasPermission = this.checkMutationPermission(event.getMember());

			if (hasPermission) {
				if (isAdd) {
					this.processAddModal(event, modalId);
				}
				if (isEdit) {
					this.processEditModal(event, modalId);
				}
			}

			if (!hasPermission) {
				event.reply(errorMessage).setEphemeral(true).queue();
			}
		}
	}

	@Override
	public void onButton(ButtonInteractionEvent event) {
		String buttonId = event.getComponentId();
		boolean isPageButton = buttonId.startsWith("annuaire:page|");

		if (isPageButton) {
			String[] parts = buttonId.split("\\|");
			boolean hasPagePart = (parts.length >= 3);

			if (hasPagePart) {
				int requestedPage = 1;
				try {
					requestedPage = Integer.parseInt(parts[2]);
				} catch (NumberFormatException ignored) {
					requestedPage = 1;
				}

				DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
				int totalEntries = repo.findAll().size();
				int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / PAGE_SIZE));
				int page = Math.min(Math.max(1, requestedPage), totalPages);

				List<DirectoryEntry> results = repo.getPaginated(page, PAGE_SIZE);
				String footer = "Page " + page + " / " + totalPages + " • " + totalEntries + " entrée(s)";
				MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed("Annuaire du département", results, footer);
				ActionRow paginationRow = DirectoryFormatter.buildPaginationRow(page, totalPages);

				event.editMessageEmbeds(embed).setComponents(paginationRow).queue();
			}
		}
	}

	private boolean checkMutationPermission(Member member) {
		boolean isAuthorized = false;
		boolean hasMember = false;
		boolean isAlumni = false;

		hasMember = (member != null);

		if (hasMember) {
			isAlumni = member.getRoles().stream()
					.map(r -> r.getName())
					.anyMatch(name -> name.equalsIgnoreCase("Les Anciens"));
			isAuthorized = !isAlumni;
		}

		return isAuthorized;
	}

	private void routeSubcommand(SlashCommandInteractionEvent event, String subcommand) {
		boolean isAdd = "ajouter".equals(subcommand);
		boolean isEdit = "modifier".equals(subcommand);
		boolean isSearch = "chercher".equals(subcommand);
		boolean isList = "lister".equals(subcommand);
		boolean isDelete = "supprimer".equals(subcommand);

		if (isAdd) {
			this.handleAdd(event);
		} else if (isEdit) {
			this.handleEdit(event);
		} else if (isSearch) {
			this.handleSearch(event);
		} else if (isList) {
			this.handleList(event);
		} else if (isDelete) {
			this.handleDelete(event);
		} else {
			event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
		}
	}

	private void handleAdd(SlashCommandInteractionEvent event) {
		String category = DirectoryCategory.PROF.name();
		String targetUserId = "NONE";
		boolean hasCategoryOption = false;
		boolean hasUserOption = false;
		TextInput lastNameInput = null;
		TextInput firstNameInput = null;
		TextInput emailInput = null;
		TextInput descInput = null;
		String modalId = "";
		Modal modal = null;

		hasCategoryOption = (event.getOption("categorie") != null);
		if (hasCategoryOption) {
			category = event.getOption("categorie").getAsString();
		}

		hasUserOption = (event.getOption("utilisateur") != null);
		if (hasUserOption) {
			targetUserId = event.getOption("utilisateur").getAsUser().getId();
		}

		lastNameInput = TextInput.create("lastName", TextInputStyle.SHORT).setRequired(true).build();
		firstNameInput = TextInput.create("firstName", TextInputStyle.SHORT).setRequired(false).build();
		emailInput = TextInput.create("email", TextInputStyle.SHORT).setRequired(true).build();
		descInput = TextInput.create("description", TextInputStyle.PARAGRAPH).setRequired(false).build();

		modalId = "annuaire:add|" + category + "|" + targetUserId;

		modal = Modal.create(modalId, "Ajouter une entrée")
				.addComponents(
						Label.of("Nom", lastNameInput),
						Label.of("Prénom", firstNameInput),
						Label.of("Adresse e-mail", emailInput),
						Label.of("Description", descInput))
				.build();

		event.replyModal(modal).queue();
	}

	private void processAddModal(ModalInteractionEvent event, String modalId) {
		String[] parts = modalId.split("\\|");
		DirectoryCategory category = DirectoryCategory.fromString(parts[1]);
		String targetUserIdStr = parts[2];
		Long discordUserId = null;
		boolean hasValidUserId = !targetUserIdStr.equals("NONE");
		String lastName = DirectoryFormatter.formatLastName(event.getValue("lastName").getAsString(), category);
		String firstName = null;
		String email = DirectoryFormatter.normalizeEmail(event.getValue("email").getAsString());
		String description = null;
		boolean isEmailValid = DirectoryFormatter.isValidEmail(email);
		String responseMessage = "Adresse e-mail invalide. Vérifiez le format (ex: prenom.nom@umontpellier.fr).";
		boolean success = false;

		if (hasValidUserId) {
			discordUserId = Long.parseLong(targetUserIdStr);
		}

		if (event.getValue("firstName") != null) {
			firstName = DirectoryFormatter.formatFirstName(event.getValue("firstName").getAsString());
		}

		if (event.getValue("description") != null && !event.getValue("description").getAsString().trim().isEmpty()) {
			description = event.getValue("description").getAsString().trim();
		}

		if (isEmailValid) {
			responseMessage = "Échec de l'ajout de l'entrée.";
			DirectoryEntry entry = new DirectoryEntry();
			entry.setLastName(lastName);
			entry.setFirstName(firstName);
			entry.setEmail(email);
			entry.setCategory(category.name());
			entry.setDescription(description);
			entry.setDiscordUserId(discordUserId);

			DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			try {
				repo.insert(entry);
				success = true;
			} catch (Exception e) {
				success = false;
			}

			if (success) {
				responseMessage = "Entrée ajoutée avec succès à l'annuaire.";
			}
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleEdit(SlashCommandInteractionEvent event) {
		long id = 0L;
		boolean hasId = false;
		DirectoryRepository repo = null;
		Optional<DirectoryEntry> entryOpt = Optional.empty();
		boolean isPresent = false;
		DirectoryEntry entry = null;
		TextInput lastNameInput = null;
		TextInput firstNameInput = null;
		TextInput emailInput = null;
		TextInput descInput = null;
		String modalId = "";
		Modal modal = null;
		String errorMessage = "Échec de la modification. Entrée introuvable.";
		boolean hasFirstName = false;
		boolean hasDesc = false;

		hasId = (event.getOption("id") != null);
		if (hasId) {
			id = event.getOption("id").getAsLong();
			repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			entryOpt = repo.findByPrimaryKey(id);
			isPresent = entryOpt.isPresent();

			if (isPresent) {
				entry = entryOpt.get();

				lastNameInput = TextInput.create("lastName", TextInputStyle.SHORT)
						.setRequired(true)
						.setValue(entry.getLastName())
						.build();

				TextInput.Builder firstNameBuilder = TextInput.create("firstName", TextInputStyle.SHORT)
						.setRequired(false);

				hasFirstName = (entry.getFirstName() != null && !entry.getFirstName().isEmpty());
				if (hasFirstName) {
					firstNameBuilder.setValue(entry.getFirstName());
				}

				firstNameInput = firstNameBuilder.build();

				emailInput = TextInput.create("email", TextInputStyle.SHORT)
						.setRequired(true)
						.setValue(entry.getEmail())
						.build();

				TextInput.Builder descBuilder = TextInput.create("description", TextInputStyle.PARAGRAPH)
						.setRequired(false);

				hasDesc = (entry.getDescription() != null && !entry.getDescription().isEmpty());
				if (hasDesc) {
					descBuilder.setValue(entry.getDescription());
				}

				descInput = descBuilder.build();

				modalId = "annuaire:edit|" + id;

				modal = Modal.create(modalId, "Modifier une entrée")
						.addComponents(
								Label.of("Nom", lastNameInput),
								Label.of("Prénom", firstNameInput),
								Label.of("Adresse e-mail", emailInput),
								Label.of("Description", descInput))
						.build();

				event.replyModal(modal).queue();
			}
		}

		if (!isPresent) {
			event.reply(errorMessage).setEphemeral(true).queue();
		}
	}

	private void processEditModal(ModalInteractionEvent event, String modalId) {
		String[] parts = modalId.split("\\|");
		long id = Long.parseLong(parts[1]);
		String rawLastName = event.getValue("lastName").getAsString();
		String firstName = null;
		String email = DirectoryFormatter.normalizeEmail(event.getValue("email").getAsString());
		String description = null;
		boolean isEmailValid = DirectoryFormatter.isValidEmail(email);
		String responseMessage = "Adresse e-mail invalide. Vérifiez le format (ex: prenom.nom@umontpellier.fr).";
		boolean success = false;

		if (event.getValue("firstName") != null) {
			firstName = DirectoryFormatter.formatFirstName(event.getValue("firstName").getAsString());
		}

		if (event.getValue("description") != null && !event.getValue("description").getAsString().trim().isEmpty()) {
			description = event.getValue("description").getAsString().trim();
		}

		if (isEmailValid) {
			responseMessage = "Échec de la modification de l'entrée.";
			DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			Optional<DirectoryEntry> entryOpt = repo.findByPrimaryKey(id);
			boolean isPresent = entryOpt.isPresent();

			if (isPresent) {
				DirectoryEntry entry = entryOpt.get();
				DirectoryCategory category = DirectoryCategory.fromString(entry.getCategory());
				entry.setLastName(DirectoryFormatter.formatLastName(rawLastName, category));
				entry.setFirstName(firstName);
				entry.setEmail(email);
				entry.setDescription(description);

				try {
					repo.update(entry);
					success = true;
				} catch (Exception e) {
					success = false;
				}
			}

			if (success) {
				responseMessage = "Entrée modifiée avec succès.";
			}
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleSearch(SlashCommandInteractionEvent event) {
		String query = "";
		boolean hasQuery = (event.getOption("recherche") != null);
		List<DirectoryEntry> results = List.of();
		String title = "Résultats de recherche";
		String footer = "0 résultat";

		if (hasQuery) {
			query = event.getOption("recherche").getAsString();
			title = "Recherche : « " + query + " »";
			DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			results = repo.searchDirectory(query);
			footer = results.size() + " résultat(s)";
		}

		MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed(title, results, footer);
		event.replyEmbeds(embed).setEphemeral(true).queue();
	}

	private void handleList(SlashCommandInteractionEvent event) {
		int page = 1;
		boolean hasPage = (event.getOption("page") != null);

		if (hasPage) {
			page = (int) event.getOption("page").getAsLong();
		}

		if (page < 1) {
			page = 1;
		}

		DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
		int totalEntries = repo.findAll().size();
		int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / PAGE_SIZE));

		if (page > totalPages) {
			page = totalPages;
		}

		List<DirectoryEntry> results = repo.getPaginated(page, PAGE_SIZE);
		String footer = "Page " + page + " / " + totalPages + " • " + totalEntries + " entrée(s)";
		MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed("Annuaire du département", results, footer);
		ActionRow paginationRow = DirectoryFormatter.buildPaginationRow(page, totalPages);

		event.replyEmbeds(embed).addComponents(paginationRow).setEphemeral(true).queue();
	}

	private void handleDelete(SlashCommandInteractionEvent event) {
		long id = 0L;
		String responseMessage = "Échec de la suppression. Entrée introuvable ou ID invalide.";
		boolean hasId = (event.getOption("id") != null);

		if (hasId) {
			id = event.getOption("id").getAsLong();
			DirectoryRepository repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			Optional<DirectoryEntry> entryOpt = repo.findByPrimaryKey(id);
			boolean isPresent = entryOpt.isPresent();

			if (isPresent) {
				int deletedCount = repo.deleteByPrimaryKey(id);

				if (deletedCount > 0) {
					responseMessage = "Entrée supprimée avec succès.";
				}
			}
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}
}