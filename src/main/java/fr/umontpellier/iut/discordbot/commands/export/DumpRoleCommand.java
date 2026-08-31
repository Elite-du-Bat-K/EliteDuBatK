package fr.umontpellier.iut.discordbot.commands.export;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class DumpRoleCommand extends AbstractCommand {

	private static final String ROLE_OPTION = "role";

	public DumpRoleCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("dump_role", "Exporte la liste des membres d'un rôle au format CSV")
				.addOption(OptionType.ROLE, ROLE_OPTION, "Le rôle à exporter", true)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		OptionMapping roleOpt = event.getOption(ROLE_OPTION);
		boolean isValid = (guild != null && roleOpt != null);

		if (isValid) {
			Role targetRole = roleOpt.getAsRole();
			event.deferReply(true).queue(
					success -> processRoleFetch(guild, targetRole, event),
					error -> System.err.println("API Timeout deferReply: " + error.getMessage()));
		}

		if (!isValid) {
			event.reply("Serveur ou paramètres introuvables.").setEphemeral(true).queue(
					null,
					error -> System.err.println("API Timeout reply: " + error.getMessage()));
		}
	}

	private void processRoleFetch(Guild guild, Role targetRole, SlashCommandInteractionEvent event) {
		guild.findMembersWithRoles(targetRole).onSuccess(members -> {
			processDump(members, targetRole, event);
		}).onError(error -> {
			event.getHook().sendMessage("Erreur de l'API lors de la récupération des membres : " + error.getMessage())
					.queue(null, hookError -> System.err.println("API Timeout hook error: " + hookError.getMessage()));
		});
	}

	private void processDump(List<Member> members, Role role, SlashCommandInteractionEvent event) {
		StringBuilder csvBuilder = new StringBuilder();
		csvBuilder.append("username,pseudo,member_id\n");

		for (Member member : members) {
			String username = escapeCsv(member.getUser().getName());
			String pseudo = escapeCsv(member.getEffectiveName());
			String memberId = member.getId();

			csvBuilder.append(username).append(",")
					.append(pseudo).append(",")
					.append(memberId).append("\n");
		}

		byte[] csvBytes = csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
		String safeRoleName = role.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
		String fileName = "dump_" + safeRoleName + ".csv";
		FileUpload fileUpload = FileUpload.fromData(csvBytes, fileName);

		event.getHook().sendFiles(fileUpload).queue(
				success -> System.out.println("Dump CSV executed successfully."),
				error -> {
					System.err.println("API Timeout / File upload failed: " + error.getMessage());
					event.getHook().sendMessage("Impossible d'envoyer le fichier (Timeout ou fichier trop volumineux).")
							.queue(
									null, fallbackError -> System.err
											.println("Fallback message failed: " + fallbackError.getMessage()));
				});
	}

	private String escapeCsv(String input) {
		String output = input;
		boolean isNotNull = (input != null);

		if (isNotNull) {
			boolean needsEscaping = input.contains(",") || input.contains("\"") || input.contains("\n");

			if (needsEscaping) {
				output = "\"" + input.replace("\"", "\"\"") + "\"";
			}
		}

		if (!isNotNull) {
			output = "";
		}

		return output;
	}
}