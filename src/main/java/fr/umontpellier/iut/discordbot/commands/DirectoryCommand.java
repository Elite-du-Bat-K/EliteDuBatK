package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import fr.umontpellier.iut.discordbot.database.repositories.DirectoryRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInput.Builder;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;
import java.util.Optional;

public class DirectoryCommand extends AbstractCommand {

	public DirectoryCommand(Bot bot) {
		super(bot);
	}

	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData addData = new SubcommandData("ajouter", "Ajouter une entrée")
				.addOption(OptionType.STRING, "categorie", "Prof ou Service Administratif", true)
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
		String category = "Prof";
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
		String[] parts = null;
		String category = "";
		String targetUserIdStr = "";
		Long discordUserId = null;
		boolean hasValidUserId = false;
		String lastName = "";
		String firstName = null;
		String email = "";
		String description = null;
		boolean hasFirstName = false;
		boolean hasDescription = false;
		DirectoryRepository repo = null;
		DirectoryEntry entry = null;
		String responseMessage = "Échec de l'ajout de l'entrée.";
		boolean success = false;

		parts = modalId.split("\\|");
		category = parts[1];
		targetUserIdStr = parts[2];

		hasValidUserId = !targetUserIdStr.equals("NONE");
		if (hasValidUserId) {
			discordUserId = Long.parseLong(targetUserIdStr);
		}

		lastName = event.getValue("lastName").getAsString();

		hasFirstName = (event.getValue("firstName") != null && !event.getValue("firstName").getAsString().isEmpty());
		if (hasFirstName) {
			firstName = event.getValue("firstName").getAsString();
		}

		email = event.getValue("email").getAsString();

		hasDescription = (event.getValue("description") != null
				&& !event.getValue("description").getAsString().isEmpty());
		if (hasDescription) {
			description = event.getValue("description").getAsString();
		}

		entry = new DirectoryEntry();
		entry.setLastName(lastName);
		entry.setFirstName(firstName);
		entry.setEmail(email);
		entry.setCategory(category);
		entry.setDescription(description);
		entry.setDiscordUserId(discordUserId);

		repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);

		try {
			repo.insert(entry);
			success = true;
		} catch (Exception e) {
			success = false;
		}

		if (success) {
			responseMessage = "Entrée ajoutée avec succès à l'annuaire.";
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
		String[] parts = null;
		long id = 0L;
		String lastName = "";
		String firstName = null;
		String email = "";
		String description = null;
		boolean hasFirstName = false;
		boolean hasDescription = false;
		DirectoryRepository repo = null;
		Optional<DirectoryEntry> entryOpt = Optional.empty();
		boolean isPresent = false;
		DirectoryEntry entry = null;
		String responseMessage = "Échec de la modification de l'entrée.";
		boolean success = false;

		parts = modalId.split("\\|");
		id = Long.parseLong(parts[1]);

		lastName = event.getValue("lastName").getAsString();

		hasFirstName = (event.getValue("firstName") != null && !event.getValue("firstName").getAsString().isEmpty());
		if (hasFirstName) {
			firstName = event.getValue("firstName").getAsString();
		}

		email = event.getValue("email").getAsString();

		hasDescription = (event.getValue("description") != null
				&& !event.getValue("description").getAsString().isEmpty());
		if (hasDescription) {
			description = event.getValue("description").getAsString();
		}

		repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
		entryOpt = repo.findByPrimaryKey(id);
		isPresent = entryOpt.isPresent();

		if (isPresent) {
			entry = entryOpt.get();
			entry.setLastName(lastName);
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

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleSearch(SlashCommandInteractionEvent event) {
		String query = "";
		boolean hasQuery = false;
		DirectoryRepository repo = null;
		List<DirectoryEntry> results = null;
		StringBuilder sb = new StringBuilder();
		String responseMessage = "Aucune entrée trouvée.";
		boolean hasResults = false;
		boolean isTooLong = false;

		hasQuery = (event.getOption("recherche") != null);
		if (hasQuery) {
			query = event.getOption("recherche").getAsString();
			repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			results = repo.searchDirectory(query);
			hasResults = !results.isEmpty();

			if (hasResults) {
				for (DirectoryEntry entry : results) {
					sb.append("**").append(entry.getLastName()).append("**");
					if (entry.getFirstName() != null) {
						sb.append(" ").append(entry.getFirstName());
					}
					sb.append(" (").append(entry.getCategory()).append(") - ");
					sb.append(entry.getEmail()).append(" [ID: ").append(entry.getId()).append("]\n");
				}
				responseMessage = sb.toString();
			}
		}

		isTooLong = (responseMessage.length() > 2000);
		if (isTooLong) {
			responseMessage = responseMessage.substring(0, 1996) + "...";
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleList(SlashCommandInteractionEvent event) {
		int page = 1;
		boolean hasPage = false;
		boolean isInvalidPage = false;
		DirectoryRepository repo = null;
		List<DirectoryEntry> results = null;
		StringBuilder sb = new StringBuilder();
		String responseMessage = "L'annuaire est vide ou la page est introuvable.";
		boolean hasResults = false;
		int pageSize = 10;
		boolean isTooLong = false;

		hasPage = (event.getOption("page") != null);
		if (hasPage) {
			page = (int) event.getOption("page").getAsLong();
		}

		isInvalidPage = (page < 1);
		if (isInvalidPage) {
			page = 1;
		}

		repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
		results = repo.getPaginated(page, pageSize);
		hasResults = !results.isEmpty();

		if (hasResults) {
			sb.append("=== Annuaire (Page ").append(page).append(") ===\n");
			for (DirectoryEntry entry : results) {
				sb.append("**").append(entry.getLastName()).append("**");
				if (entry.getFirstName() != null) {
					sb.append(" ").append(entry.getFirstName());
				}
				sb.append(" (").append(entry.getCategory()).append(") - ");
				sb.append(entry.getEmail()).append(" [ID: ").append(entry.getId()).append("]\n");
			}
			responseMessage = sb.toString();
		}

		isTooLong = (responseMessage.length() > 2000);
		if (isTooLong) {
			responseMessage = responseMessage.substring(0, 1996) + "...";
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleDelete(SlashCommandInteractionEvent event) {
		long id = 0L;
		String responseMessage = "Échec de la suppression. Entrée introuvable ou ID invalide.";
		boolean hasId = false;
		DirectoryRepository repo = null;
		Optional<DirectoryEntry> entryOpt = Optional.empty();
		boolean isPresent = false;
		int deletedCount = 0;
		boolean isDeleted = false;

		hasId = (event.getOption("id") != null);
		if (hasId) {
			id = event.getOption("id").getAsLong();
			repo = this.getBot().getRepositories().getRepository(DirectoryRepository.class);
			entryOpt = repo.findByPrimaryKey(id);
			isPresent = entryOpt.isPresent();

			if (isPresent) {
				deletedCount = repo.deleteByPrimaryKey(id);
				isDeleted = (deletedCount > 0);
				if (isDeleted) {
					responseMessage = "Entrée supprimée avec succès.";
				}
			}
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}
}