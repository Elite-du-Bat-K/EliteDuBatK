package fr.umontpellier.iut.discordbot.commands.annuaire;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryCategory;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.services.DirectoryService;
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

		return Commands.slash("annuaire", "Gérer l'annuaire")
				.addSubcommands(addData, editData, searchData, listData, deleteData);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subcommand = event.getSubcommandName();
		boolean hasSubcommand = subcommand != null;

		if (hasSubcommand) {
			this.routeSubcommand(event, subcommand);
		}

		if (!hasSubcommand) {
			event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
		}
	}

	@Override
	public void onModal(ModalInteractionEvent event) {
		String modalId = event.getModalId();
		boolean isAdd = modalId.startsWith("annuaire:add|");
		boolean isEdit = modalId.startsWith("annuaire:edit|");

		if (isAdd) {
			this.processAddModal(event, modalId);
		}

		if (isEdit) {
			this.processEditModal(event, modalId);
		}
	}

	@Override
	public void onButton(ButtonInteractionEvent event) {
		String buttonId = event.getComponentId();
		boolean isPageButton = buttonId.startsWith("annuaire:page|");

		if (isPageButton) {
			String[] parts = buttonId.split("\\|");
			boolean hasPagePart = parts.length >= 3;

			if (hasPagePart) {
				int requestedPage = 1;
				try {
					requestedPage = Integer.parseInt(parts[2]);
				} catch (NumberFormatException ignored) {
					requestedPage = 1;
				}

				DirectoryService.PageResult pageResult = this.getBot().getDirectoryService()
						.getPage(requestedPage, PAGE_SIZE);
				String footer = "Page " + pageResult.page() + " / " + pageResult.totalPages() + " • "
						+ pageResult.totalEntries() + " entrée(s)";
				MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed("Annuaire du département",
						pageResult.entries(), footer);
				ActionRow paginationRow = DirectoryFormatter.buildPaginationRow(pageResult.page(),
						pageResult.totalPages());

				event.editMessageEmbeds(embed).setComponents(paginationRow).queue();
			}
		}
	}

	private void routeSubcommand(SlashCommandInteractionEvent event, String subcommand) {
		boolean isAdd = "ajouter".equals(subcommand);
		boolean isEdit = "modifier".equals(subcommand);
		boolean isSearch = "chercher".equals(subcommand);
		boolean isList = "lister".equals(subcommand);
		boolean isDelete = "supprimer".equals(subcommand);

		if (isAdd) {
			this.handleAdd(event);
		}
		if (isEdit) {
			this.handleEdit(event);
		}
		if (isSearch) {
			this.handleSearch(event);
		}
		if (isList) {
			this.handleList(event);
		}
		if (isDelete) {
			this.handleDelete(event);
		}
	}

	private void handleAdd(SlashCommandInteractionEvent event) {
		String category = DirectoryCategory.PROF.name();
		String targetUserId = "NONE";

		if (event.getOption("categorie") != null) {
			category = event.getOption("categorie").getAsString();
		}

		if (event.getOption("utilisateur") != null) {
			targetUserId = event.getOption("utilisateur").getAsUser().getId();
		}

		TextInput lastNameInput = TextInput.create("lastName", TextInputStyle.SHORT).setRequired(true).build();
		TextInput firstNameInput = TextInput.create("firstName", TextInputStyle.SHORT).setRequired(false).build();
		TextInput emailInput = TextInput.create("email", TextInputStyle.SHORT).setRequired(true).build();
		TextInput descInput = TextInput.create("description", TextInputStyle.PARAGRAPH).setRequired(false).build();

		Modal modal = Modal.create("annuaire:add|" + category + "|" + targetUserId, "Ajouter une entrée")
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
		Long discordUserId = null;

		if (!"NONE".equals(parts[2])) {
			discordUserId = Long.parseLong(parts[2]);
		}

		String rawLastName = event.getValue("lastName").getAsString();
		String rawFirstName = event.getValue("firstName") != null ? event.getValue("firstName").getAsString() : null;
		String rawEmail = event.getValue("email").getAsString();
		String rawDesc = event.getValue("description") != null ? event.getValue("description").getAsString() : null;
		boolean isAdmin = event.getMember() != null && this.isAdmin(event.getMember());

		DirectoryService.MutationResult result = this.getBot().getDirectoryService().requestAdd(
				category, rawLastName, rawFirstName, rawEmail, rawDesc, discordUserId,
				event.getUser().getId(), isAdmin);

		event.reply(result.message()).setEphemeral(true).queue();
	}

	private void handleEdit(SlashCommandInteractionEvent event) {
		boolean isPresent = false;

		if (event.getOption("id") != null) {
			long id = event.getOption("id").getAsLong();
			Optional<DirectoryEntry> entryOpt = this.getBot().getDirectoryService().findEntryById(id);
			isPresent = entryOpt.isPresent();

			if (isPresent) {
				DirectoryEntry entry = entryOpt.get();
				TextInput lastNameInput = TextInput.create("lastName", TextInputStyle.SHORT)
						.setRequired(true)
						.setValue(entry.getLastName())
						.build();

				TextInput.Builder firstNameBuilder = TextInput.create("firstName", TextInputStyle.SHORT)
						.setRequired(false);
				if (entry.getFirstName() != null && !entry.getFirstName().isEmpty()) {
					firstNameBuilder.setValue(entry.getFirstName());
				}

				TextInput emailInput = TextInput.create("email", TextInputStyle.SHORT)
						.setRequired(true)
						.setValue(entry.getEmail())
						.build();

				TextInput.Builder descBuilder = TextInput.create("description", TextInputStyle.PARAGRAPH)
						.setRequired(false);
				if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
					descBuilder.setValue(entry.getDescription());
				}

				Modal modal = Modal.create("annuaire:edit|" + id, "Modifier une entrée")
						.addComponents(
								Label.of("Nom", lastNameInput),
								Label.of("Prénom", firstNameBuilder.build()),
								Label.of("Adresse e-mail", emailInput),
								Label.of("Description", descBuilder.build()))
						.build();

				event.replyModal(modal).queue();
			}
		}

		if (!isPresent) {
			event.reply("Échec de la modification. Entrée introuvable.").setEphemeral(true).queue();
		}
	}

	private void processEditModal(ModalInteractionEvent event, String modalId) {
		String[] parts = modalId.split("\\|");
		long id = Long.parseLong(parts[1]);
		String rawLastName = event.getValue("lastName").getAsString();
		String rawFirstName = event.getValue("firstName") != null ? event.getValue("firstName").getAsString() : null;
		String rawEmail = event.getValue("email").getAsString();
		String rawDesc = event.getValue("description") != null ? event.getValue("description").getAsString() : null;
		boolean isAdmin = event.getMember() != null && this.isAdmin(event.getMember());

		DirectoryService.MutationResult result = this.getBot().getDirectoryService().requestEdit(
				id, rawLastName, rawFirstName, rawEmail, rawDesc, event.getUser().getId(), isAdmin);

		event.reply(result.message()).setEphemeral(true).queue();
	}

	private void handleSearch(SlashCommandInteractionEvent event) {
		List<DirectoryEntry> results = List.of();
		String title = "Résultats de recherche";
		String footer = "0 résultat";

		if (event.getOption("recherche") != null) {
			String query = event.getOption("recherche").getAsString();
			title = "Recherche : « " + query + " »";
			results = this.getBot().getDirectoryService().search(query);
			footer = results.size() + " résultat(s)";
		}

		MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed(title, results, footer);
		event.replyEmbeds(embed).setEphemeral(true).queue();
	}

	private void handleList(SlashCommandInteractionEvent event) {
		int requestedPage = 1;

		if (event.getOption("page") != null) {
			requestedPage = (int) event.getOption("page").getAsLong();
		}

		DirectoryService.PageResult pageResult = this.getBot().getDirectoryService()
				.getPage(requestedPage, PAGE_SIZE);
		String footer = "Page " + pageResult.page() + " / " + pageResult.totalPages() + " • "
				+ pageResult.totalEntries() + " entrée(s)";
		MessageEmbed embed = DirectoryFormatter.buildDirectoryEmbed("Annuaire du département",
				pageResult.entries(), footer);
		ActionRow paginationRow = DirectoryFormatter.buildPaginationRow(pageResult.page(), pageResult.totalPages());

		event.replyEmbeds(embed).addComponents(paginationRow).setEphemeral(true).queue();
	}

	private void handleDelete(SlashCommandInteractionEvent event) {
		String responseMessage = "Échec de la suppression. Entrée introuvable ou ID invalide.";

		if (event.getOption("id") != null) {
			long id = event.getOption("id").getAsLong();
			boolean isAdmin = event.getMember() != null && this.isAdmin(event.getMember());
			DirectoryService.MutationResult result = this.getBot().getDirectoryService()
					.requestDelete(id, event.getUser().getId(), isAdmin);
			responseMessage = result.message();
		}

		event.reply(responseMessage).setEphemeral(true).queue();
	}
}