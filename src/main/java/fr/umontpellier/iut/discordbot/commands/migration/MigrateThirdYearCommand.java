package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MigrateThirdYearCommand extends AbstractCommand {

	public MigrateThirdYearCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_a3", "Lance la migration de la troisième année vers les anciens")
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		boolean isValid = (guild != null);

		if (isValid) {
			event.deferReply(true).queue();

			guild.loadMembers().onSuccess(members -> {
				processMigration(guild, members, event);
			}).onError(error -> {
				event.getHook().sendMessage("Erreur lors du chargement des membres.").queue();
			});
		}

		if (!isValid) {
			event.reply("Serveur introuvable.").setEphemeral(true).queue();
		}
	}

	private void processMigration(Guild guild, List<Member> members, SlashCommandInteractionEvent event) {
		Role a3Role = getRoleByName(guild, "Année 3");
		Role anciensRole = getRoleByName(guild, "Les Anciens");
		boolean rolesValid = (a3Role != null && anciensRole != null);

		if (rolesValid) {
			Map<Member, RoleChanges> roleChangesMap = new HashMap<>();
			Map<Member, String> nicknameChangesMap = new HashMap<>();
			MigrationReport report = new MigrationReport();
			String yearSuffix = " [" + Year.now().getValue() + "]";

			for (Member member : members) {
				boolean isA3 = member.getRoles().contains(a3Role);

				if (isA3) {
					addChange(roleChangesMap, member, anciensRole, true);
					addChange(roleChangesMap, member, a3Role, false);

					for (Role role : member.getRoles()) {
						boolean isGX = role.getName().matches("G[1-4]|G-Sète");
						if (isGX) {
							addChange(roleChangesMap, member, role, false);
						}
					}

					String originalName = member.getEffectiveName();
					String newName = originalName;
					int maxLen = 32 - yearSuffix.length();
					boolean tooLong = (originalName.length() > maxLen);

					if (tooLong) {
						newName = originalName.substring(0, maxLen);
					}

					newName = newName + yearSuffix;
					nicknameChangesMap.put(member, newName);
					report.addSuccess(originalName + " -> " + newName);
				}
			}

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			futures.addAll(commitRoleChanges(guild, roleChangesMap));
			futures.addAll(commitNicknameChanges(guild, nicknameChangesMap));
			futures.addAll(applyCategoryDeletions(guild));
			futures.add(applyGeneralChannelDeletion(guild));

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, exception) -> {
				boolean hasExecutionError = (exception != null);

				if (hasExecutionError) {
					event.getHook().sendMessage("La migration s'est terminée avec des erreurs partielles.").queue();
				}

				if (!hasExecutionError) {
					String summary = report.formatSummary();
					boolean isTooLong = summary.length() > 2000;

					if (isTooLong) {
						event.getHook().sendMessage("Migration terminée. Le rapport est trop long pour être affiché.")
								.queue();
					}

					if (!isTooLong) {
						event.getHook().sendMessage(summary).queue();
					}
				}
			});
		}

		if (!rolesValid) {
			event.getHook()
					.sendMessage("Erreur : les rôles 'Année 3' ou 'Les Anciens' sont introuvables sur le serveur.")
					.queue();
		}
	}

	private CompletableFuture<Void> applyGeneralChannelDeletion(Guild guild) {
		CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
		List<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> channels = guild
				.getTextChannelsByName("🎓│général-a3", true);
		boolean hasChannel = !channels.isEmpty();

		if (hasChannel) {
			outputFuture = channels.get(0).delete().submit();
		}

		return outputFuture;
	}

	private List<CompletableFuture<Void>> applyCategoryDeletions(Guild guild) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		List<Category> categories = guild.getCategories();

		for (Category category : categories) {
			String name = category.getName();
			boolean isTarget = name.matches("G[1-4]|G-Sète");

			if (isTarget) {
				List<CompletableFuture<Void>> channelFutures = new ArrayList<>();
				for (GuildChannel channel : category.getChannels()) {
					channelFutures.add(channel.delete().submit());
				}

				CompletableFuture<Void> categoryFuture = CompletableFuture
						.allOf(channelFutures.toArray(new CompletableFuture[0]))
						.thenCompose(ignored -> category.delete().submit());

				futures.add(categoryFuture);
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

	private List<CompletableFuture<Void>> commitNicknameChanges(Guild guild, Map<Member, String> nicknameChangesMap) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<Member, String> entry : nicknameChangesMap.entrySet()) {
			Member member = entry.getKey();
			String newNickname = entry.getValue();
			futures.add(member.modifyNickname(newNickname).submit());
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

	private static class MigrationReport {
		private final List<String> succeeded = new ArrayList<>();

		public void addSuccess(String item) {
			succeeded.add(item);
		}

		public String formatSummary() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Rapport global de migration A3 -> Anciens\n");
			sb.append("- Étudiants migrés : ").append(succeeded.size()).append("\n");

			boolean hasSuccesses = !succeeded.isEmpty();

			if (hasSuccesses) {
				sb.append("\n## Nouveaux pseudos générés :\n");
				for (String entry : succeeded) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}