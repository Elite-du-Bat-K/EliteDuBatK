package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FixChannelNamesCommand extends AbstractCommand {

	public FixChannelNamesCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("fix_gx_channels", "Corrige le préfixe des salons Gx")
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		boolean isValid = (guild != null);

		if (isValid) {
			event.deferReply(false).queue();
			processFix(guild, event);
		}

		if (!isValid) {
			event.reply("Serveur introuvable.").setEphemeral(true).queue();
		}
	}

	private void processFix(Guild guild, SlashCommandInteractionEvent event) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		List<Category> categories = guild.getCategories();
		FixReport report = new FixReport();

		for (Category category : categories) {
			String catName = category.getName();
			boolean isTarget = catName.matches("G[1-4]|G-Sète");

			if (isTarget) {
				for (GuildChannel channel : category.getChannels()) {
					String oldName = channel.getName();
					boolean hasContent = !oldName.isEmpty();

					if (hasContent) {
						String truncated = oldName.substring(1);
						String newName = "g" + truncated;
						boolean needsRename = !oldName.equals(newName);

						if (needsRename) {
							futures.add(channel.getManager().setName(newName).submit());
							report.addRenamed(oldName, newName);
						}
					}
				}
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, exception) -> {
			boolean hasExecutionError = (exception != null);

			if (hasExecutionError) {
				event.getHook().sendMessage("La commande s'est terminée avec des erreurs partielles.").queue();
			}

			sendChunkedMessage(event.getHook(), report.formatSummary());
		});
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

		public void addRenamed(String oldName, String newName) {
			renamed.add(oldName + " -> " + newName);
		}

		public String formatSummary() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Rapport de correction des noms de salons\n");
			sb.append("- Salons renommés : ").append(renamed.size()).append("\n");

			boolean hasRenamed = !renamed.isEmpty();

			if (hasRenamed) {
				sb.append("\n## Détails :\n");
				for (String entry : renamed) {
					sb.append("- `").append(entry).append("`\n");
				}
			}

			return sb.toString();
		}
	}
}