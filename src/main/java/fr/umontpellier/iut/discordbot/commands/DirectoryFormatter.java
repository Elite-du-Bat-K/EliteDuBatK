package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryCategory;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class DirectoryFormatter {
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	private static final Color EMBED_COLOR = new Color(47, 128, 237);

	public static String formatLastName(String rawLastName, DirectoryCategory category) {
		String formatted = "";
		boolean hasValue = (rawLastName != null);

		if (hasValue) {
			formatted = rawLastName.trim();
			boolean isProf = (category == DirectoryCategory.PROF);
			if (isProf) {
				formatted = formatted.toUpperCase(Locale.FRENCH);
			}
		}

		return formatted;
	}

	public static String formatFirstName(String rawFirstName) {
		String formatted = null;
		boolean hasValue = (rawFirstName != null && !rawFirstName.trim().isEmpty());

		if (hasValue) {
			String cleaned = rawFirstName.trim().toLowerCase(Locale.FRENCH);
			StringBuilder result = new StringBuilder();
			boolean capitalizeNext = true;

			for (int i = 0; i < cleaned.length(); i = i + 1) {
				char current = cleaned.charAt(i);
				boolean isSeparator = (current == '-' || current == ' ' || current == '\'');

				if (isSeparator) {
					result.append(current);
					capitalizeNext = true;
				}

				if (!isSeparator) {
					char output = current;
					if (capitalizeNext) {
						output = Character.toUpperCase(current);
						capitalizeNext = false;
					}
					result.append(output);
				}
			}

			formatted = result.toString();
		}

		return formatted;
	}

	public static String normalizeEmail(String rawEmail) {
		String normalized = "";
		boolean hasValue = (rawEmail != null);

		if (hasValue) {
			normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
		}

		return normalized;
	}

	public static boolean isValidEmail(String email) {
		boolean isValid = false;
		boolean hasValue = (email != null && email.length() <= 254);

		if (hasValue) {
			isValid = EMAIL_PATTERN.matcher(email).matches();
		}

		return isValid;
	}

	public static MessageEmbed buildDirectoryEmbed(String title, List<DirectoryEntry> entries, String footerText) {
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle(title);
		builder.setColor(EMBED_COLOR);

		boolean isEmpty = (entries == null || entries.isEmpty());
		if (isEmpty) {
			builder.setDescription("Aucune entrée trouvée.");
		}

		if (!isEmpty) {
			for (DirectoryEntry entry : entries) {
				DirectoryCategory category = DirectoryCategory.fromString(entry.getCategory());
				StringBuilder fieldTitle = new StringBuilder();
				StringBuilder fieldValue = new StringBuilder();

				fieldTitle.append(category.getIcon()).append(" ").append(entry.getLastName());
				boolean hasFirstName = (entry.getFirstName() != null && !entry.getFirstName().isEmpty());
				if (hasFirstName) {
					fieldTitle.append(" ").append(entry.getFirstName());
				}

				fieldValue.append("📧 ```text\n").append(entry.getEmail()).append("\n```");

				boolean hasDiscordUser = (entry.getDiscordUserId() != null);
				if (hasDiscordUser) {
					fieldValue.append(" • <@").append(entry.getDiscordUserId()).append(">");
				}

				boolean hasDescription = (entry.getDescription() != null && !entry.getDescription().isEmpty());
				if (hasDescription) {
					fieldValue.append("\n*").append(entry.getDescription()).append("*");
				}

				fieldValue.append("\n`#").append(entry.getId()).append("` • ").append(category.getDisplayName());

				builder.addField(fieldTitle.toString(), fieldValue.toString(), false);
			}
		}

		boolean hasFooter = (footerText != null && !footerText.isEmpty());
		if (hasFooter) {
			builder.setFooter(footerText);
		}

		MessageEmbed embed = builder.build();
		return embed;
	}

	public static ActionRow buildPaginationRow(int currentPage, int totalPages) {
		int prevPage = Math.max(1, currentPage - 1);
		int nextPage = Math.min(totalPages, currentPage + 1);
		boolean isFirstPage = (currentPage <= 1);
		boolean isLastPage = (currentPage >= totalPages);

		Button prevButton = Button.secondary("annuaire:page|prev|" + prevPage, "◀ Précédent")
				.withDisabled(isFirstPage);
		Button nextButton = Button.secondary("annuaire:page|next|" + nextPage, "Suivant ▶")
				.withDisabled(isLastPage);

		ActionRow row = ActionRow.of(prevButton, nextButton);
		return row;
	}
}