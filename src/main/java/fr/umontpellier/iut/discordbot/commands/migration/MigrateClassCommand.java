package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class MigrateClassCommand extends AbstractCommand {

	private static final String OPTION_FILE = "file";
	private static final String OPTION_CLASS = "classe";
	private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	public MigrateClassCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_class", "Lance la migration vers une classe via un fichier d'emails")
				.addOption(OptionType.STRING, OPTION_CLASS, "Nom de la classe cible (ex: S1, Q2, G1)", true)
				.addOption(OptionType.ATTACHMENT, OPTION_FILE, "Fichier texte contenant les adresses mail", true);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		OptionMapping fileOption = event.getOption(OPTION_FILE);
		OptionMapping classOption = event.getOption(OPTION_CLASS);
		boolean isValid = (guild != null && fileOption != null && classOption != null);

		if (isValid) {
			event.deferReply(true).queue();
			
			String targetClassName = Objects.requireNonNull(classOption).getAsString().trim();
			Message.Attachment attachment = Objects.requireNonNull(fileOption).getAsAttachment();

			attachment.getProxy().download().thenAccept(inputStream -> {
				List<String> emails = extractEmails(inputStream);
				MigrationReport report = processMigration(guild, emails, targetClassName);
				event.getHook().sendMessage(report.formatSummary()).queue();
			}).exceptionally(throwable -> {
				event.getHook().sendMessage("Erreur lors de l'exécution : " + throwable.getMessage()).queue();
				return null;
			});
		}

		if (!isValid) {
			event.reply("Paramètres invalides ou serveur inaccessible.").setEphemeral(true).queue();
		}
	}

	private List<String> extractEmails(InputStream inputStream) {
		List<String> emails = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					emails.add(trimmed);
				}
			}
		} catch (Exception ignored) {
		}
		return emails;
	}

	private MigrationReport processMigration(Guild guild, List<String> emails, String className) {
		MigrationReport report = new MigrationReport(className);
		List<Role> matchingRoles = guild.getRolesByName(className, true);

		if (matchingRoles.isEmpty()) {
			report.setRoleFound(false);
			return report;
		}

		Role classRole = matchingRoles.get(0);
		guild.loadMembers().onSuccess(members -> {
			for (String email : emails) {
				ParsedIdentity identity = parseEmail(email);
				Member matchedMember = findMember(members, identity.firstName(), identity.lastName());

				if (matchedMember == null) {
					matchedMember = findMember(members, identity.lastName(), identity.firstName());
				}

				if (matchedMember != null) {
					guild.addRoleToMember(matchedMember, classRole).queue();
					report.addSuccess(matchedMember.getEffectiveName() + " (" + email + ")");
				} else {
					report.addFailure(email);
				}
			}
		});

		return report;
	}

	private ParsedIdentity parseEmail(String email) {
		String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
		localPart = localPart.replaceAll("\\d+$", "");

		String[] parts = localPart.split("\\.", 2);
		String first = parts.length > 0 ? parts[0] : "";
		String last = parts.length > 1 ? parts[1] : "";

		return new ParsedIdentity(first, last);
	}

	private Member findMember(List<Member> members, String firstName, String lastName) {
		Member matched = null;
		String normalizedFirst = normalize(firstName);
		String normalizedLast = normalize(lastName);

		for (Member member : members) {
			String normalizedDisplayName = normalize(member.getEffectiveName());

			if (matchesIdentity(normalizedDisplayName, normalizedFirst, normalizedLast)) {
				matched = member;
				break;
			}
		}

		return matched;
	}

	private boolean matchesIdentity(String displayName, String firstName, String lastName) {
		if (firstName.isEmpty() || lastName.isEmpty()) {
			return false;
		}

		String[] nameParts = displayName.split("[\\s\\-_]+");
		if (nameParts.length < 2) {
			return false;
		}

		boolean startsWithFirst = nameParts[0].equalsIgnoreCase(firstName) || firstName.startsWith(nameParts[0]);
		boolean matchesLastInitialOrMore = lastName.startsWith(nameParts[1].substring(0, 1));

		return startsWithFirst && matchesLastInitialOrMore;
	}

	private String normalize(String input) {
		if (input == null) {
			return "";
		}
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
		return DIACRITICS_PATTERN.matcher(normalized).replaceAll("").toLowerCase().trim();
	}

	private record ParsedIdentity(String firstName, String lastName) {}

	private static class MigrationReport {
		private final String className;
		private boolean roleFound = true;
		private final List<String> succeeded = new ArrayList<>();
		private final List<String> failed = new ArrayList<>();

		public MigrationReport(String className) {
			this.className = className;
		}

		public void setRoleFound(boolean roleFound) {
			this.roleFound = roleFound;
		}

		public void addSuccess(String item) {
			succeeded.add(item);
		}

		public void addFailure(String item) {
			failed.add(item);
		}

		public String formatSummary() {
			if (!roleFound) {
				return "Rôle introuvable sur le serveur : " + className;
			}

			StringBuilder sb = new StringBuilder();
			sb.append("Migration vers la classe **").append(className).append("** terminée.\n");
			sb.append("Membres traités avec succès : ").append(succeeded.size()).append("\n");
			sb.append("Échecs/Introuvables : ").append(failed.size()).append("\n");

			if (!failed.isEmpty()) {
				sb.append("\n**Liste des adresses non trouvées :**\n");
				for (String email : failed) {
					sb.append("- `").append(email).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}