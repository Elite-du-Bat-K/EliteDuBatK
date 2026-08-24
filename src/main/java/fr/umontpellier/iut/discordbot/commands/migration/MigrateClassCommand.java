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
import java.util.stream.Collectors;

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
				processMigration(guild, emails, targetClassName, event);
			}).exceptionally(throwable -> {
				event.getHook().sendMessage("Erreur lors du traitement du fichier : " + throwable.getMessage()).queue();
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

	private void processMigration(Guild guild, List<String> emails, String className,
			SlashCommandInteractionEvent event) {
		List<Role> matchingRoles = guild.getRolesByName(className, true);

		if (matchingRoles.isEmpty()) {
			event.getHook().sendMessage("Rôle introuvable sur le serveur : " + className).queue();
			return;
		}

		Role classRole = matchingRoles.get(0);

		guild.loadMembers().onSuccess(members -> {
			MigrationReport report = new MigrationReport(className);

			for (String email : emails) {
				ParsedIdentity identity = parseEmail(email);
				List<Member> candidates = findCandidates(members, identity.firstName(), identity.lastName());

				if (candidates.isEmpty()) {
					candidates = findCandidates(members, identity.lastName(), identity.firstName());
				}

				if (candidates.size() == 1) {
					Member member = candidates.get(0);
					guild.addRoleToMember(member, classRole).queue();
					report.addSuccess(member.getEffectiveName() + " (" + email + ")");
				} else if (candidates.size() > 1) {
					report.addAmbiguous(email, candidates);
				} else {
					report.addNotFound(email);
				}
			}

			event.getHook().sendMessage(report.formatSummary()).queue();
		});
	}

	private ParsedIdentity parseEmail(String email) {
		String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
		localPart = localPart.replaceAll("\\d+$", "");

		String[] parts = localPart.split("\\.", 2);
		String first = parts.length > 0 ? parts[0] : "";
		String last = parts.length > 1 ? parts[1] : "";

		return new ParsedIdentity(first, last);
	}

	private List<Member> findCandidates(List<Member> members, String firstName, String lastName) {
		List<Member> candidates = new ArrayList<>();
		String normalizedFirst = normalize(firstName);
		String normalizedLast = normalize(lastName);

		if (!(normalizedFirst.isEmpty() || normalizedLast.isEmpty())) {
			for (Member member : members) {
				String normalizedDisplayName = normalize(member.getEffectiveName());

				if (matchesIdentity(normalizedDisplayName, normalizedFirst, normalizedLast)) {
					candidates.add(member);
				}
			}
		}

		return candidates;
	}

	private boolean matchesIdentity(String displayName, String firstName, String lastName) {
		boolean output = false;
		String[] nameParts = displayName.split("[\\s\\-_]+");

		if (nameParts.length >= 2) {
			boolean startsWithFirst = nameParts[0].equalsIgnoreCase(firstName) || firstName.startsWith(nameParts[0]);
			boolean matchesLast = lastName.startsWith(nameParts[1]);

			output = startsWithFirst && matchesLast;
		}

		return output;
	}

	private String normalize(String input) {
		String output = "";

		if (input != null) {
			String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

			output = DIACRITICS_PATTERN.matcher(normalized).replaceAll("").toLowerCase().trim();
		}

		return output;
	}

	private record ParsedIdentity(String firstName, String lastName) {
	}

	private static class AmbiguousEntry {
		private final String email;
		private final List<Member> candidates;

		public AmbiguousEntry(String email, List<Member> candidates) {
			this.email = email;
			this.candidates = candidates;
		}

		public String getEmail() {
			return email;
		}

		public List<Member> getCandidates() {
			return candidates;
		}
	}

	private static class MigrationReport {
		private final String className;
		private final List<String> succeeded = new ArrayList<>();
		private final List<String> notFound = new ArrayList<>();
		private final List<AmbiguousEntry> ambiguous = new ArrayList<>();

		public MigrationReport(String className) {
			this.className = className;
		}

		public void addSuccess(String item) {
			succeeded.add(item);
		}

		public void addNotFound(String email) {
			notFound.add(email);
		}

		public void addAmbiguous(String email, List<Member> candidates) {
			ambiguous.add(new AmbiguousEntry(email, candidates));
		}

		public String formatSummary() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Rapport de migration vers **").append(className).append("** :\n");
			sb.append("- Succès : ").append(succeeded.size()).append("\n");
			sb.append("- Non trouvés : ").append(notFound.size()).append("\n");
			sb.append("- Ambiguïtés : ").append(ambiguous.size()).append("\n");

			if (!ambiguous.isEmpty()) {
				sb.append("\n## Adresses ambiguës (conflits détectés) :**\n");
				for (AmbiguousEntry entry : ambiguous) {
					String usernames = entry.getCandidates().stream()
							.map(m -> "`@" + m.getUser().getName() + "` (" + m.getEffectiveName() + ")")
							.collect(Collectors.joining(", "));
					sb.append("- `").append(entry.getEmail()).append("` -> Correspondances : ").append(usernames)
							.append("\n");
				}
			}

			if (!notFound.isEmpty()) {
				sb.append("\n## **Adresses non trouvées :**\n");
				for (String email : notFound) {
					sb.append("- `").append(email).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}