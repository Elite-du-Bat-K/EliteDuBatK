package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class FixAnciensYearCommand extends AbstractCommand {

	private static final Pattern YEAR_PATTERN = Pattern.compile(".*\\[\\d{4}\\]$");

	public FixAnciensYearCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands
				.slash("fix_anciens_year", "Retrouve et assigne l'année d'obtention du rôle aux Anciens via les logs")
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		boolean isValid = (guild != null);

		if (isValid) {
			System.out.println("[DEBUG] Starting fix_anciens_year on guild: " + guild.getName());
			event.deferReply(false).queue();

			guild.loadMembers().onSuccess(members -> {
				System.out.println("[DEBUG] Loaded " + members.size() + " members from guild.");
				processFix(guild, members, event);
			}).onError(error -> {
				System.err.println("[DEBUG] Error loading members: " + error.getMessage());
				event.getHook().sendMessage("Erreur lors du chargement des membres.").queue();
			});
		}

		if (!isValid) {
			System.err.println("[DEBUG] Guild is null, aborting command.");
			event.reply("Serveur introuvable.").setEphemeral(true).queue();
		}
	}

	private void processFix(Guild guild, List<Member> members, SlashCommandInteractionEvent event) {
		Role anciensRole = getRoleByName(guild, "Les Anciens");
		boolean isRoleValid = (anciensRole != null);

		if (isRoleValid) {
			List<Member> targetMembers = getTargetMembers(members, anciensRole);
			Map<Long, Integer> userYearMap = new HashMap<>();
			AtomicInteger logEntriesProcessed = new AtomicInteger(0);

			boolean hasTargets = !targetMembers.isEmpty();
			System.out.println("[DEBUG] Identified " + targetMembers.size() + " target members needing year tag.");

			if (hasTargets) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).forEachAsync(entry -> {
					int count = logEntriesProcessed.incrementAndGet();
					boolean shouldLogStep = (count % 100 == 0);
					if (shouldLogStep) {
						System.out.println("[DEBUG] Processed " + count + " audit log entries so far...");
					}

					processAuditLogEntry(entry, targetMembers, anciensRole, userYearMap);
					return true;
				}).whenComplete((ignored, exception) -> {
					boolean hasError = (exception != null);

					if (hasError) {
						System.err.println("[DEBUG] Error reading audit logs: " + exception.getMessage());
						event.getHook().sendMessage("Erreur lors de la lecture des logs d'audit.").queue();
					}

					if (!hasError) {
						System.out.println("[DEBUG] Audit logs scan complete. Total entries scanned: "
								+ logEntriesProcessed.get());
						System.out.println("[DEBUG] Years recovered for " + userYearMap.size() + " out of "
								+ targetMembers.size() + " targets.");
						applyNicknameChanges(targetMembers, userYearMap, event);
					}
				});
			}

			if (!hasTargets) {
				System.out.println("[DEBUG] No target members found without year tag.");
				event.getHook().sendMessage("Aucun ancien sans année détecté. Tout est à jour !").queue();
			}
		}

		if (!isRoleValid) {
			System.err.println("[DEBUG] Role 'Les Anciens' not found in guild.");
			event.getHook().sendMessage("Erreur : le rôle 'Les Anciens' est introuvable sur le serveur.").queue();
		}
	}

	private List<Member> getTargetMembers(List<Member> members, Role anciensRole) {
		List<Member> targetMembers = new ArrayList<>();

		for (Member member : members) {
			boolean hasRole = member.getRoles().contains(anciensRole);
			boolean isMissingYear = !YEAR_PATTERN.matcher(member.getEffectiveName()).matches();
			boolean isTarget = (hasRole && isMissingYear);

			if (isTarget) {
				targetMembers.add(member);
			}
		}

		return targetMembers;
	}

	private void processAuditLogEntry(AuditLogEntry entry, List<Member> targetMembers, Role anciensRole,
			Map<Long, Integer> userYearMap) {
		long targetId = entry.getTargetIdLong();
		boolean isTrackedUser = false;

		for (Member member : targetMembers) {
			boolean isMatch = (member.getIdLong() == targetId);
			if (isMatch) {
				isTrackedUser = true;
			}
		}

		if (isTrackedUser) {
			AuditLogChange addChange = entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD);
			boolean hasAddChange = (addChange != null);

			if (hasAddChange) {
				Object rawValue = addChange.getNewValue();
				String changeData = String.valueOf(rawValue);
				boolean roleFound = changeData.contains(anciensRole.getId());

				if (roleFound) {
					int year = entry.getTimeCreated().getYear();
					boolean isNewEntry = !userYearMap.containsKey(targetId);

					if (isNewEntry) {
						System.out.println("[DEBUG] Recovered year " + year + " for user ID: " + targetId);
						userYearMap.put(targetId, year);
					}
				}
			}
		}
	}

	private void applyNicknameChanges(List<Member> targetMembers, Map<Long, Integer> userYearMap,
			SlashCommandInteractionEvent event) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		FixReport report = new FixReport();

		for (Member member : targetMembers) {
			long id = member.getIdLong();
			boolean hasYearFound = userYearMap.containsKey(id);

			if (hasYearFound) {
				int year = userYearMap.get(id);
				String yearSuffix = " [" + year + "]";
				String originalName = member.getEffectiveName();
				String newName = originalName;
				int maxLen = 32 - yearSuffix.length();
				boolean tooLong = (originalName.length() > maxLen);

				if (tooLong) {
					newName = originalName.substring(0, maxLen);
				}

				newName = newName + yearSuffix;
				System.out.println("[DEBUG] Renaming: '" + originalName + "' -> '" + newName + "'");

				boolean hasHierarchyIssue = false;
				CompletableFuture<Void> renameFuture = null;

				try {
					renameFuture = member.modifyNickname(newName).submit();
				} catch (net.dv8tion.jda.api.exceptions.HierarchyException e) {
					hasHierarchyIssue = true;
				}

				if (!hasHierarchyIssue && renameFuture != null) {
					futures.add(renameFuture);
					report.addRenamed(originalName, newName);
				}

				if (hasHierarchyIssue) {
					System.err.println("[DEBUG] HierarchyException ignored for admin/owner: " + originalName);
					report.addHierarchyError(originalName);
				}
			}

			if (!hasYearFound) {
				System.out.println(
						"[DEBUG] Missing from logs (>90 days): '" + member.getEffectiveName() + "' (ID: " + id + ")");
				report.addMissing(member.getEffectiveName());
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, exception) -> {
			boolean hasExecutionError = (exception != null);

			if (hasExecutionError) {
				System.err.println(
						"[DEBUG] Partial error during async nickname modifications: " + exception.getMessage());
				event.getHook().sendMessage("La commande s'est terminée avec des erreurs asynchrones partielles.")
						.queue();
			}

			if (!hasExecutionError) {
				System.out.println("[DEBUG] Nickname update batch finished with zero async errors.");
			}

			sendChunkedMessage(event.getHook(), report.formatSummary());
		});
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

	private void sendChunkedMessage(InteractionHook hook, String message) {
		int length = message.length();
		boolean isTooLong = length > 1900;

		if (!isTooLong) {
			hook.sendMessage(message).queue();
		}

		if (isTooLong) {
			String[] lines = message.split("\n");
			StringBuilder currentChunk = new StringBuilder();

			for (String line : lines) {
				boolean willOverflow = (currentChunk.length() + line.length() + 1) > 1900;

				if (willOverflow) {
					hook.sendMessage(currentChunk.toString()).queue();
					currentChunk = new StringBuilder();
				}

				currentChunk.append(line).append("\n");
			}

			boolean hasRemaining = currentChunk.length() > 0;
			if (hasRemaining) {
				hook.sendMessage(currentChunk.toString()).queue();
			}
		}
	}

	private static class FixReport {
		private final List<String> renamed = new ArrayList<>();
		private final List<String> missing = new ArrayList<>();
		private final List<String> hierarchyErrors = new ArrayList<>();

		public void addRenamed(String oldName, String newName) {
			renamed.add(oldName + " -> " + newName);
		}

		public void addMissing(String name) {
			missing.add(name);
		}

		public void addHierarchyError(String name) {
			hierarchyErrors.add(name);
		}

		public String formatSummary() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Rapport de correction des Anciens\n");
			sb.append("- Pseudos corrigés : ").append(renamed.size()).append("\n");
			sb.append("- Non trouvés dans les logs : ").append(missing.size()).append("\n");
			sb.append("- Intouchables (Admins) : ").append(hierarchyErrors.size()).append("\n");

			boolean hasRenamed = !renamed.isEmpty();
			if (hasRenamed) {
				sb.append("\n## Pseudos mis à jour :\n");
				for (String entry : renamed) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			boolean hasHierarchyErrors = !hierarchyErrors.isEmpty();
			if (hasHierarchyErrors) {
				sb.append("\n## Pseudos intouchables (Hiérarchie Discord / Admins) :\n");
				for (String entry : hierarchyErrors) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			boolean hasMissing = !missing.isEmpty();
			if (hasMissing) {
				sb.append("\n## Absents des logs d'audit (Historique > 90 jours) :\n");
				for (String entry : missing) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}