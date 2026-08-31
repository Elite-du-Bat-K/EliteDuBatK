package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.attribute.ICopyableChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MigrateFirstYearCommand extends AbstractCommand {

	private static final String Q1_FILE = "q1_file";
	private static final String Q2_FILE = "q2_file";
	private static final String Q3_FILE = "q3_file";
	private static final String Q4_FILE = "q4_file";
	private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	public MigrateFirstYearCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_a1", "Lance la migration A1 vers A2")
				.addOption(OptionType.ATTACHMENT, Q1_FILE, "Fichier texte adresses mail Q1", true)
				.addOption(OptionType.ATTACHMENT, Q2_FILE, "Fichier texte adresses mail Q2", true)
				.addOption(OptionType.ATTACHMENT, Q3_FILE, "Fichier texte adresses mail Q3", true)
				.addOption(OptionType.ATTACHMENT, Q4_FILE, "Fichier texte adresses mail Q4", true)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		OptionMapping q1Opt = event.getOption(Q1_FILE);
		OptionMapping q2Opt = event.getOption(Q2_FILE);
		OptionMapping q3Opt = event.getOption(Q3_FILE);
		OptionMapping q4Opt = event.getOption(Q4_FILE);

		boolean isValid = (guild != null && q1Opt != null && q2Opt != null && q3Opt != null && q4Opt != null);

		if (isValid) {
			event.deferReply(true).queue();

			CompletableFuture<List<String>> q1Future = downloadEmails(q1Opt);
			CompletableFuture<List<String>> q2Future = downloadEmails(q2Opt);
			CompletableFuture<List<String>> q3Future = downloadEmails(q3Opt);
			CompletableFuture<List<String>> q4Future = downloadEmails(q4Opt);

			CompletableFuture.allOf(q1Future, q2Future, q3Future, q4Future).whenComplete((ignored, exception) -> {
				boolean hasDownloadError = (exception != null);

				if (hasDownloadError) {
					event.getHook().sendMessage("Erreur lors du téléchargement des fichiers.").queue();
				}

				if (!hasDownloadError) {
					List<String> q1Emails = q1Future.join();
					List<String> q2Emails = q2Future.join();
					List<String> q3Emails = q3Future.join();
					List<String> q4Emails = q4Future.join();

					guild.loadMembers().onSuccess(members -> {
						processMigration(guild, members, q1Emails, q2Emails, q3Emails, q4Emails, event);
					}).onError(error -> {
						event.getHook().sendMessage("Erreur lors du chargement des membres.").queue();
					});
				}
			});
		}

		if (!isValid) {
			event.reply("Paramètres invalides ou serveur inaccessible.").setEphemeral(true).queue();
		}
	}

	private CompletableFuture<List<String>> downloadEmails(OptionMapping option) {
		CompletableFuture<List<String>> outputFuture = new CompletableFuture<>();
		boolean isNotNull = (option != null);

		if (isNotNull) {
			Message.Attachment attachment = option.getAsAttachment();
			attachment.getProxy().download().whenComplete((inputStream, throwable) -> {
				boolean hasError = (throwable != null);
				if (hasError) {
					outputFuture.completeExceptionally(throwable);
				}
				if (!hasError) {
					List<String> emails = extractEmails(inputStream);
					outputFuture.complete(emails);
				}
			});
		}

		if (!isNotNull) {
			outputFuture.complete(new ArrayList<>());
		}

		return outputFuture;
	}

	private List<String> extractEmails(InputStream inputStream) {
		List<String> emails = new ArrayList<>();
		boolean isValidStream = (inputStream != null);

		if (isValidStream) {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String trimmed = line.trim();
					boolean notEmpty = !trimmed.isEmpty();
					if (notEmpty) {
						emails.add(trimmed);
					}
				}
			} catch (Exception ignored) {
			}
		}

		return emails;
	}

	private void processMigration(Guild guild, List<Member> members, List<String> q1, List<String> q2, List<String> q3,
			List<String> q4, SlashCommandInteractionEvent event) {
		Map<Member, RoleChanges> roleChangesMap = new HashMap<>();
		MigrationReport report = new MigrationReport();

		applyA1AndSeteLogic(guild, members, roleChangesMap);

		applyQListLogic(guild, members, q1, "Q1", roleChangesMap, report);
		applyQListLogic(guild, members, q2, "Q2", roleChangesMap, report);
		applyQListLogic(guild, members, q3, "Q3", roleChangesMap, report);
		applyQListLogic(guild, members, q4, "Q4", roleChangesMap, report);

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		futures.addAll(commitRoleChanges(guild, roleChangesMap));
		futures.addAll(applyChannelResets(guild));
		futures.add(applyGeneralChannelUpdate(guild));

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, exception) -> {
			boolean hasExecutionError = (exception != null);

			if (hasExecutionError) {
				event.getHook().sendMessage("La migration s'est terminée avec des erreurs partielles.").queue();
			}

			if (!hasExecutionError) {
				String summary = report.formatSummary();
				boolean isTooLong = summary.length() > 2000;

				if (isTooLong) {
					event.getHook().sendMessage(
							"Migration terminée. Le rapport est trop long pour être affiché en un seul message.")
							.queue();
				}

				if (!isTooLong) {
					event.getHook().sendMessage(summary).queue();
				}
			}
		});
	}

	private CompletableFuture<Void> applyGeneralChannelUpdate(Guild guild) {
		CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
		List<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> channels = guild
				.getTextChannelsByName("🎓│général-a1", true);
		Role a1Role = getRoleByName(guild, "Année 1");
		Role a2Role = getRoleByName(guild, "Année 2");
		boolean isValid = (!channels.isEmpty() && a1Role != null && a2Role != null);

		if (isValid) {
			net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = channels.get(0);
			List<Permission> viewPermission = new ArrayList<>();
			viewPermission.add(Permission.VIEW_CHANNEL);

			outputFuture = channel.createCopy()
					.submit()
					.thenCompose(newChannel -> channel.getManager()
							.setName("🎓│général-a2")
							.putRolePermissionOverride(a2Role.getIdLong(), viewPermission, null)
							.putRolePermissionOverride(a1Role.getIdLong(), null, viewPermission)
							.submit());
		}

		return outputFuture;
	}

	private void applyA1AndSeteLogic(Guild guild, List<Member> members, Map<Member, RoleChanges> roleChangesMap) {
		Role a1Role = getRoleByName(guild, "Année 1");
		Role a2Role = getRoleByName(guild, "Année 2");
		Role sSeteRole = getRoleByName(guild, "S-Sète");
		Role qSeteRole = getRoleByName(guild, "Q-Sète");
		Role modoSSeteRole = getRoleByName(guild, "Modo S-Sète");
		Role modoQSeteRole = getRoleByName(guild, "Modo Q-Sète");

		for (Member member : members) {
			List<Role> memberRoles = member.getRoles();
			boolean isSSete = memberRoles.contains(sSeteRole);
			boolean isA1 = memberRoles.contains(a1Role);

			if (isSSete) {
				addChange(roleChangesMap, member, qSeteRole, true);
				addChange(roleChangesMap, member, a2Role, true);
				addChange(roleChangesMap, member, sSeteRole, false);
				addChange(roleChangesMap, member, a1Role, false);

				boolean isModoSSete = memberRoles.contains(modoSSeteRole);
				if (isModoSSete) {
					addChange(roleChangesMap, member, modoQSeteRole, true);
					addChange(roleChangesMap, member, modoSSeteRole, false);
				}
			}

			if (!isSSete && isA1) {
				addChange(roleChangesMap, member, a1Role, false);

				for (Role role : memberRoles) {
					boolean isSX = role.getName().matches("S[1-6]");
					if (isSX) {
						addChange(roleChangesMap, member, role, false);
					}
				}
			}
		}
	}

	private void applyQListLogic(Guild guild, List<Member> members, List<String> emails, String qName,
			Map<Member, RoleChanges> roleChangesMap, MigrationReport report) {
		Role a2Role = getRoleByName(guild, "Année 2");
		Role qRole = getRoleByName(guild, qName);
		Role a3Role = getRoleByName(guild, "Année 3");

		for (String email : emails) {
			ParsedIdentity identity = parseEmail(email);
			List<Member> candidates = findCandidates(members, identity.firstName(), identity.lastName());
			boolean isEmpty = candidates.isEmpty();

			if (isEmpty) {
				candidates = findCandidates(members, identity.lastName(), identity.firstName());
			}

			int candidateCount = candidates.size();

			if (candidateCount == 1) {
				Member member = candidates.get(0);
				try {
					addChange(roleChangesMap, member, a2Role, true);
					addChange(roleChangesMap, member, qRole, true);

					boolean isA3 = member.getRoles().contains(a3Role);
					if (isA3) {
						addChange(roleChangesMap, member, a3Role, false);
						for (Role role : member.getRoles()) {
							boolean isGX = role.getName().matches("G\\d+");
							if (isGX) {
								addChange(roleChangesMap, member, role, false);
							}
						}
					}

					report.addSuccess(member.getEffectiveName() + " (" + email + ") -> " + qName);
				} catch (HierarchyException e) {
					System.err.println("Impossible de migrer " + member.getEffectiveName() + ". (HierarchyException)");
				}
			} else if (candidateCount > 1) {
				report.addAmbiguous(email, candidates);
			} else if (candidateCount == 0) {
				report.addNotFound(email);
			}
		}
	}

	private List<CompletableFuture<Void>> applyChannelResets(Guild guild) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		List<Category> categories = guild.getCategories();

		for (Category category : categories) {
			String name = category.getName();
			boolean isTarget = name.matches("S[1-6]") || name.equals("S-Sète");

			if (isTarget) {
				for (GuildChannel channel : category.getChannels()) {
					boolean isCopyable = channel instanceof ICopyableChannel;

					if (isCopyable) {
						ICopyableChannel copyable = (ICopyableChannel) channel;
						CompletableFuture<Void> copyFuture = copyable.createCopy()
								.submit()
								.thenCompose(ignored -> channel.delete().submit());
						futures.add(copyFuture);
					}
				}
			}
		}

		return futures;
	}

	private List<CompletableFuture<Void>> commitRoleChanges(Guild guild, Map<Member, RoleChanges> roleChangesMap) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<Member, RoleChanges> entry : roleChangesMap.entrySet()) {
			Member member = entry.getKey();
			RoleChanges changes = entry.getValue();

			boolean hasChanges = !(changes.getToAdd().isEmpty() && changes.getToRemove().isEmpty());

			if (hasChanges) {
				futures.add(guild.modifyMemberRoles(member, changes.getToAdd(), changes.getToRemove()).submit());
			}
		}

		return futures;
	}

	private void addChange(Map<Member, RoleChanges> map, Member member, Role role, boolean isAdd) {
		boolean isValid = (member != null && role != null);

		if (isValid) {
			RoleChanges changes = map.computeIfAbsent(member, k -> new RoleChanges());

			if (isAdd) {
				changes.addRole(role);
			}

			if (!isAdd) {
				changes.removeRole(role);
			}
		}
	}

	private Role getRoleByName(Guild guild, String name) {
		Role targetRole = null;
		List<Role> roles = guild.getRolesByName(name, true);
		boolean hasRole = !roles.isEmpty();

		if (hasRole) {
			targetRole = roles.get(0);
		}

		return targetRole;
	}

	private ParsedIdentity parseEmail(String email) {
		boolean hasAt = email.contains("@");
		String localPart = email;

		if (hasAt) {
			localPart = email.substring(0, email.indexOf('@'));
		}

		localPart = localPart.replaceAll("\\d+$", "");
		String[] parts = localPart.split("\\.", 2);

		String first = "";
		String last = "";
		boolean hasFirst = (parts.length > 0);
		boolean hasLast = (parts.length > 1);

		if (hasFirst) {
			first = parts[0];
		}

		if (hasLast) {
			last = parts[1];
		}

		return new ParsedIdentity(first, last);
	}

	private List<Member> findCandidates(List<Member> members, String firstName, String lastName) {
		List<Member> candidates = new ArrayList<>();
		String normalizedFirst = normalize(firstName);
		String normalizedLast = normalize(lastName);
		boolean isValid = !(normalizedFirst.isEmpty() || normalizedLast.isEmpty());

		if (isValid) {
			for (Member member : members) {
				String normalizedDisplayName = normalize(member.getEffectiveName());
				boolean isMatch = matchesIdentity(normalizedDisplayName, normalizedFirst, normalizedLast);

				if (isMatch) {
					candidates.add(member);
				}
			}
		}

		return candidates;
	}

	private boolean matchesIdentity(String displayName, String firstName, String lastName) {
		boolean output = false;
		String[] nameParts = displayName.split("[\\s\\-_]+");
		boolean hasEnoughParts = (nameParts.length >= 2);

		if (hasEnoughParts) {
			boolean startsWithFirst = nameParts[0].equalsIgnoreCase(firstName) || firstName.startsWith(nameParts[0]);
			boolean matchesLast = lastName.startsWith(nameParts[1]);
			output = (startsWithFirst && matchesLast);
		}

		return output;
	}

	private String normalize(String input) {
		String output = "";
		boolean isNotNull = (input != null);

		if (isNotNull) {
			String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
			output = DIACRITICS_PATTERN.matcher(normalized).replaceAll("").toLowerCase().trim();
		}

		return output;
	}

	private static class RoleChanges {
		private final List<Role> toAdd = new ArrayList<>();
		private final List<Role> toRemove = new ArrayList<>();

		public void addRole(Role role) {
			boolean canAdd = !toAdd.contains(role) && !toRemove.contains(role);
			if (canAdd) {
				toAdd.add(role);
			}
		}

		public void removeRole(Role role) {
			boolean canRemove = !toRemove.contains(role) && !toAdd.contains(role);
			if (canRemove) {
				toRemove.add(role);
			}
		}

		public List<Role> getToAdd() {
			return toAdd;
		}

		public List<Role> getToRemove() {
			return toRemove;
		}
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
		private final List<String> succeeded = new ArrayList<>();
		private final List<String> notFound = new ArrayList<>();
		private final List<AmbiguousEntry> ambiguous = new ArrayList<>();

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
			sb.append("# Rapport global de migration A1 -> A2\n");
			sb.append("- Succès : ").append(succeeded.size()).append("\n");
			sb.append("- Non trouvés : ").append(notFound.size()).append("\n");
			sb.append("- Ambiguïtés : ").append(ambiguous.size()).append("\n");

			boolean hasAmbiguous = !ambiguous.isEmpty();
			if (hasAmbiguous) {
				sb.append("\n## Adresses ambiguës (conflits détectés) :\n");
				for (AmbiguousEntry entry : ambiguous) {
					String usernames = entry.getCandidates().stream()
							.map(m -> "`@" + m.getUser().getName() + "` (" + m.getEffectiveName() + ")")
							.collect(Collectors.joining(", "));
					sb.append("- `").append(entry.getEmail()).append("` -> Correspondances : ").append(usernames)
							.append("\n");
				}
			}

			boolean hasNotFound = !notFound.isEmpty();
			if (hasNotFound) {
				sb.append("\n## Adresses non trouvées :\n");
				for (String email : notFound) {
					sb.append("- `").append(email).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}
