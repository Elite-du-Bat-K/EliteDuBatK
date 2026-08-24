package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class MigrateChannelsCommand extends AbstractCommand {

	private static final String FUTURE_ROLE_PREFIX = "Future ";
	private static final List<String> CHANNEL_SUFFIXES = List.of("│annonces", "│général", "│vocal");

	private record PermissionOverrideData(boolean role, long id, long allowed, long denied) {}
	private record GroupMigration(Role oldRole, Role newRole, Category oldCategory) {}
	private record PendingMigration(long userId, String guildId, List<GroupMigration> migrations) {}

	private final Map<String, PendingMigration> pendingMigrations = new ConcurrentHashMap<>();

	public MigrateChannelsCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_channels", "Migre automatiquement les salons vers les rôles Future");
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		if (!event.isFromGuild()) {
			event.reply("❌ Cette commande ne peut être utilisée que sur un serveur.").setEphemeral(true).queue();
			return;
		}

		Member member = Objects.requireNonNull(event.getMember());
		if (!isAdmin(member)) {
			event.reply("❌ Vous n'avez pas la permission de migrer les salons.").setEphemeral(true).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		List<GroupMigration> migrations = detectMigrations(guild);
		if (migrations.isEmpty()) {
			event.reply("ℹ️ Aucune migration détectée.").setEphemeral(true).queue();
			return;
		}
		String permissionError = validateBotPermissions(guild, migrations);
		if (permissionError != null) {
			event.reply("❌ Migration impossible : " + permissionError).setEphemeral(true).queue();
			return;
		}

		String confirmationId = UUID.randomUUID().toString();
		pendingMigrations.put(confirmationId, new PendingMigration(member.getIdLong(), guild.getId(), migrations));
		String preview = buildPreview(migrations);
		event.reply(preview)
				.addComponents(ActionRow.of(
						Button.success("migration:confirm:" + confirmationId, "Confirmer la migration"),
						Button.danger("migration:cancel:" + confirmationId, "Annuler")))
				.setEphemeral(true)
				.queue();
	}

	public void handleButtonInteraction(ButtonInteractionEvent event) {
		String[] parts = event.getComponentId().split(":", 3);
		if (parts.length != 3 || !parts[0].equals("migration")) return;

		PendingMigration pending = pendingMigrations.get(parts[2]);
		if (pending == null) {
			event.reply("❌ Cette prévisualisation a expiré.").setEphemeral(true).queue();
			return;
		}
		Guild guild = event.getGuild();
		if (event.getUser().getIdLong() != pending.userId()
				|| guild == null || !guild.getId().equals(pending.guildId())) {
			event.reply("❌ Seul l'administrateur qui a demandé la migration peut la confirmer.")
					.setEphemeral(true).queue();
			return;
		}

		pendingMigrations.remove(parts[2]);
		if (parts[1].equals("cancel")) {
			event.editMessage("✅ Migration annulée.").setComponents(List.of()).queue();
			return;
		}

		event.deferEdit().queue();
		CompletableFuture.runAsync(() -> {
			try {
				for (GroupMigration migration : pending.migrations()) {
					migrateGroup(guild, migration);
				}
				event.getHook().editOriginal("✅ " + pending.migrations().size()
						+ " groupe(s) migré(s), anciens salons conservés.").queue();
			} catch (Exception e) {
				logger.warn("Could not migrate group channels", e);
				event.getHook().editOriginal("❌ La migration a échoué : " + e.getMessage()).queue();
			}
		});
	}

	@NotNull
	private String buildPreview(List<GroupMigration> migrations) {
		StringBuilder preview = new StringBuilder("⚠️ **Prévisualisation de la migration**\n\n");
		for (GroupMigration migration : migrations) {
			preview.append("• `").append(migration.oldRole().getName()).append("` → `")
					.append(migration.newRole().getName()).append("`: conservation de la catégorie existante et de ")
					.append(migration.oldCategory().getChannels().size())
					.append(" salon(s), puis recréation de annonces, général et vocal.\n");
		}
		preview.append("\nLes anciennes catégories et leurs salons ne seront pas supprimés. "
				+ "Les permissions seront conservées, avec l'ancien rôle remplacé par le nouveau.");
		return preview.toString();
	}

	private List<GroupMigration> detectMigrations(Guild guild) {
		return guild.getRoles().stream()
				.filter(role -> !role.getName().startsWith(FUTURE_ROLE_PREFIX))
				.map(oldRole -> new GroupMigration(
						oldRole,
						guild.getRolesByName(FUTURE_ROLE_PREFIX + oldRole.getName(), true)
								.stream().findFirst().orElse(null),
						guild.getCategoriesByName(oldRole.getName(), true)
								.stream().findFirst().orElse(null)))
				.filter(migration -> migration.newRole() != null
						&& migration.oldCategory() != null
						&& guild.getCategoriesByName(migration.newRole().getName(), true).isEmpty())
				.toList();
	}

	private void migrateGroup(Guild guild, GroupMigration migration) {
		Role oldRole = migration.oldRole();
		Role newRole = migration.newRole();
		Category oldCategory = migration.oldCategory();

		List<PermissionOverrideData> categoryOverrides = captureOverrides(oldCategory);
		Map<String, List<PermissionOverrideData>> channelOverrides = oldCategory.getChannels().stream()
				.filter(channel -> channel.getName().startsWith(oldRole.getName() + "│"))
				.collect(Collectors.toMap(
						channel -> channel.getName().substring(oldRole.getName().length()),
						channel -> captureOverrides(channel.getPermissionContainer()),
						(first, ignored) -> first));

		newRole.getManager().setPermissions(oldRole.getPermissions()).complete();

		Category newCategory = guild.createCategory(newRole.getName()).complete();
		restoreOverrides(newCategory, categoryOverrides, oldRole, newRole, guild);

		for (String suffix : CHANNEL_SUFFIXES) {
			GuildChannel newChannel = suffix.equals("│vocal")
					? newCategory.createVoiceChannel(oldRole.getName() + suffix).complete()
					: newCategory.createTextChannel(oldRole.getName() + suffix).complete();
			restoreOverrides(newChannel.getPermissionContainer(),
					channelOverrides.getOrDefault(suffix, List.of()), oldRole, newRole, guild);
		}

	}

	private String validateBotPermissions(Guild guild, List<GroupMigration> migrations) {
		Member botMember = guild.getSelfMember();
		if (!botMember.hasPermission(Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES,
				Permission.MANAGE_PERMISSIONS)) {
			return "le bot doit avoir les permissions Gérer les salons, Gérer les rôles et Gérer les permissions.";
		}
		for (GroupMigration migration : migrations) {
			if (!botMember.canInteract(migration.oldRole()) || !botMember.canInteract(migration.newRole())) {
				return "le rôle du bot doit être supérieur aux rôles " + migration.oldRole().getName()
						+ " et " + migration.newRole().getName() + ".";
			}
		}
		return null;
	}

	private List<PermissionOverrideData> captureOverrides(IPermissionContainer channel) {
		return channel.getPermissionOverrides().stream()
				.map(override -> new PermissionOverrideData(
						override.isRoleOverride(),
						override.getIdLong(),
						override.getAllowedRaw(),
						override.getDeniedRaw()))
				.toList();
	}

	private void restoreOverrides(
			IPermissionContainer channel,
			List<PermissionOverrideData> overrides,
			Role oldRole,
			Role newRole,
			Guild guild
	) {
		for (PermissionOverrideData data : overrides) {
			if (data.role()) {
				Role role = data.id() == oldRole.getIdLong() ? newRole : guild.getRoleById(data.id());
				if (role != null) {
					channel.upsertPermissionOverride(role)
							.setPermissions(data.allowed(), data.denied())
							.complete();
				}
			} else {
				Member member = guild.getMemberById(data.id());
				if (member != null) {
					channel.upsertPermissionOverride(member)
							.setPermissions(data.allowed(), data.denied())
							.complete();
				}
			}
		}
	}
}
