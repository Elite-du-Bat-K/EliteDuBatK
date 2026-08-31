package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MigrateSecondYearCommand extends AbstractCommand {

	public MigrateSecondYearCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_a2", "Lance la migration de la deuxième année vers la troisième")
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
		Role a2Role = getRoleByName(guild, "Année 2");
		Role a3Role = getRoleByName(guild, "Année 3");
		boolean rolesValid = (a2Role != null && a3Role != null);

		if (rolesValid) {
			Map<Member, RoleChanges> roleChangesMap = new HashMap<>();
			MigrationReport report = new MigrationReport();
			String[] qGroups = { "Q1", "Q2", "Q3", "Q4", "Q-Sète" };

			for (Member member : members) {
				boolean isA2 = member.getRoles().contains(a2Role);

				try {
					if (isA2) {
						addChange(roleChangesMap, member, a3Role, true);
						addChange(roleChangesMap, member, a2Role, false);

						for (String qName : qGroups) {
							Role qRole = getRoleByName(guild, qName);
							boolean hasQRole = (qRole != null && member.getRoles().contains(qRole));

							if (hasQRole) {
								String gName = qName.replace("Q", "G");
								Role gRole = getRoleByName(guild, gName);
								boolean hasGRole = (gRole != null);

								if (hasGRole) {
									addChange(roleChangesMap, member, gRole, true);
									addChange(roleChangesMap, member, qRole, false);
								}
							}

							String modoQName = "Modo " + qName;
							Role modoQRole = getRoleByName(guild, modoQName);
							boolean hasModoQRole = (modoQRole != null && member.getRoles().contains(modoQRole));

							if (hasModoQRole) {
								String modoGName = "Modo " + qName.replace("Q", "G");
								Role modoGRole = getRoleByName(guild, modoGName);
								boolean hasModoGRole = (modoGRole != null);

								if (hasModoGRole) {
									addChange(roleChangesMap, member, modoGRole, true);
									addChange(roleChangesMap, member, modoQRole, false);
								}
							}
						}
						report.addSuccess(member.getEffectiveName());
					}
				} catch (HierarchyException e) {
					System.err.println("Impossible de migrer " + member.getEffectiveName() + ". (HierarchyException)");
				}
			}

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			futures.addAll(commitRoleChanges(guild, roleChangesMap));
			futures.addAll(applyCategoryTransformations(guild, qGroups));
			futures.add(applyGeneralChannelUpdate(guild));

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, exception) -> {
				boolean hasExecutionError = (exception != null);

				if (hasExecutionError) {
					event.getHook().sendMessage("La migration s'est terminée avec des erreurs partielles.").queue();
				}

				if (!hasExecutionError) {
					String summary = report.formatSummary();
					boolean isTooLong = (summary.length() > 2000);

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
			event.getHook().sendMessage("Erreur : les rôles 'Année 2' ou 'Année 3' sont introuvables.").queue();
		}
	}

	private CompletableFuture<Void> applyGeneralChannelUpdate(Guild guild) {
		CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
		List<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> channels = guild
				.getTextChannelsByName("🎓│général-a2", true);
		Role a2Role = getRoleByName(guild, "Année 2");
		Role a3Role = getRoleByName(guild, "Année 3");
		boolean isValid = (!channels.isEmpty() && a2Role != null && a3Role != null);

		if (isValid) {
			net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = channels.get(0);
			List<Permission> viewPermission = new ArrayList<>();
			viewPermission.add(Permission.VIEW_CHANNEL);

			outputFuture = channel.getManager()
					.setName("🎓│général-a3")
					.putRolePermissionOverride(a3Role.getIdLong(), viewPermission, null)
					.putRolePermissionOverride(a2Role.getIdLong(), null, viewPermission)
					.submit();
		}

		return outputFuture;
	}

	private List<CompletableFuture<Void>> applyCategoryTransformations(Guild guild, String[] qGroups) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (String qName : qGroups) {
			String gName = qName.replace("Q", "G");
			Role qRole = getRoleByName(guild, qName);
			Role gRole = getRoleByName(guild, gName);
			boolean rolesExist = (qRole != null && gRole != null);

			if (rolesExist) {
				List<Category> categories = guild.getCategoriesByName(qName, true);
				boolean hasCategory = !categories.isEmpty();

				if (hasCategory) {
					Category category = categories.get(0);
					List<Permission> viewPermission = new ArrayList<>();
					viewPermission.add(Permission.VIEW_CHANNEL);

					CompletableFuture<Void> updateCatFuture = category.getManager()
							.setName(gName)
							.putRolePermissionOverride(gRole.getIdLong(), viewPermission, null)
							.putRolePermissionOverride(qRole.getIdLong(), null, viewPermission)
							.submit();

					futures.add(updateCatFuture);

					CompletableFuture<Void> createNewCatFuture = guild.createCategory(qName)
							.submit()
							.thenCompose(newCat -> {
								String prefix = qName.toLowerCase() + "\u2502";
								CompletableFuture<?> annonces = newCat.createTextChannel(prefix + "annonces").submit();
								CompletableFuture<?> general = newCat.createTextChannel(prefix + "général").submit();
								CompletableFuture<?> devoirs = newCat.createTextChannel(prefix + "devoirs").submit();
								CompletableFuture<?> vocal = newCat.createVoiceChannel(prefix + "vocal").submit();

								return CompletableFuture.allOf(annonces, general, devoirs, vocal);
							});

					futures.add(createNewCatFuture);
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

	private static class RoleChanges {
		private final List<Role> toAdd = new ArrayList<>();
		private final List<Role> toRemove = new ArrayList<>();

		public void addRole(Role role) {
			boolean canAdd = (!toAdd.contains(role) && !toRemove.contains(role));
			if (canAdd) {
				toAdd.add(role);
			}
		}

		public void removeRole(Role role) {
			boolean canRemove = (!toRemove.contains(role) && !toAdd.contains(role));
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
			sb.append("# Rapport global de migration A2 -> A3\n");
			sb.append("- Étudiants migrés : ").append(succeeded.size()).append("\n");

			boolean hasSuccesses = !succeeded.isEmpty();

			if (hasSuccesses) {
				sb.append("\n## Étudiants traités :\n");
				for (String entry : succeeded) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}