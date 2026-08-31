package fr.umontpellier.iut.discordbot.commands.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.managers.channel.concrete.CategoryManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

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
			event.deferReply(false).queue();

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

				if (isA2) {
					cleanTransverseRoles(roleChangesMap, member);

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
					sendChunkedMessage(event.getHook(), report.formatSummary());
				}
			});
		}

		if (!rolesValid) {
			event.getHook().sendMessage("Erreur : les rôles 'Année 2' ou 'Année 3' sont introuvables.").queue();
		}
	}

	private void cleanTransverseRoles(Map<Member, RoleChanges> map, Member member) {
		for (Role role : member.getRoles()) {
			String name = role.getName();
			boolean isDelegue = name.equals("Délégué");
			boolean isModoAnnee = name.matches("Modo Année [1-3]");
			boolean isModoClasse = name.matches("Modo [SQG]([1-6]|-Sète)");

			if (isDelegue || isModoAnnee || isModoClasse) {
				addChange(map, member, role, false);
			}
		}
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

	private CompletableFuture<Void> applyGeneralChannelUpdate(Guild guild) {
		CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
		List<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> channels = guild
				.getTextChannelsByName("🎓│général-a2", true);
		Role a2Role = getRoleByName(guild, "Année 2");
		Role a3Role = getRoleByName(guild, "Année 3");
		Role publicRole = guild.getPublicRole();
		boolean isValid = (!channels.isEmpty() && a2Role != null && a3Role != null);

		if (isValid) {
			net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = channels.get(0);
			List<Permission> viewPermission = new ArrayList<>();
			viewPermission.add(Permission.VIEW_CHANNEL);

			outputFuture = channel.getManager()
					.setName("🎓│général-a3")
					.putRolePermissionOverride(a3Role.getIdLong(), viewPermission, null)
					.putRolePermissionOverride(publicRole.getIdLong(), null, viewPermission)
					.removePermissionOverride(a2Role.getIdLong())
					.submit();
		}

		return outputFuture;
	}

	private List<CompletableFuture<Void>> applyCategoryTransformations(Guild guild, String[] qGroups) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		Role publicRole = guild.getPublicRole();

		for (String qName : qGroups) {
			String gName = qName.replace("Q", "G");
			Role qRole = getRoleByName(guild, qName);
			Role gRole = getRoleByName(guild, gName);
			Role modoQRole = getRoleByName(guild, "Modo " + qName);
			Role modoGRole = getRoleByName(guild, "Modo " + gName);

			boolean rolesExist = (qRole != null && gRole != null);

			if (rolesExist) {
				List<Category> categories = guild.getCategoriesByName(qName, true);
				boolean hasCategory = !categories.isEmpty();

				List<Permission> viewPermission = new ArrayList<>();
				viewPermission.add(Permission.VIEW_CHANNEL);

				if (hasCategory) {
					Category category = categories.get(0);

					CategoryManager manager = category.getManager()
							.setName(gName)
							.putRolePermissionOverride(publicRole.getIdLong(), null, viewPermission)
							.putRolePermissionOverride(gRole.getIdLong(), viewPermission, null)
							.removePermissionOverride(qRole.getIdLong());

					boolean hasModoGRole = (modoGRole != null);
					if (hasModoGRole) {
						manager = manager.putRolePermissionOverride(modoGRole.getIdLong(), viewPermission, null);
					}

					boolean hasModoQRole = (modoQRole != null);
					if (hasModoQRole) {
						manager = manager.removePermissionOverride(modoQRole.getIdLong());
					}

					CompletableFuture<Void> updateCatFuture = manager.submit().thenCompose(ignored -> {
						List<CompletableFuture<Void>> syncFutures = new ArrayList<>();

						for (GuildChannel channel : category.getChannels()) {
							String oldName = channel.getName();
							String newName = oldName.replace(qName.toLowerCase(), gName.toLowerCase());
							boolean isCategorizable = (channel instanceof ICategorizableChannel);
							CompletableFuture<Void> channelFuture = null;

							if (isCategorizable) {
								ICategorizableChannel catChannel = (ICategorizableChannel) channel;
								channelFuture = catChannel.getManager().setName(newName).sync().submit();
							} else {
								channelFuture = channel.getManager().setName(newName).submit();
							}

							syncFutures.add(channelFuture);
						}

						return CompletableFuture.allOf(syncFutures.toArray(new CompletableFuture[0]));
					});

					futures.add(updateCatFuture);

					ChannelAction<Category> createAction = guild.createCategory(qName)
							.addRolePermissionOverride(publicRole.getIdLong(), null, viewPermission)
							.addRolePermissionOverride(qRole.getIdLong(), viewPermission, null);

					if (hasModoQRole) {
						createAction = createAction.addRolePermissionOverride(modoQRole.getIdLong(), viewPermission,
								null);
					}

					CompletableFuture<Void> createNewCatFuture = createAction.submit().thenCompose(newCat -> {
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
			} else {
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