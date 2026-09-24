package fr.umontpellier.iut.discordbot.events.messages;

import java.time.Instant;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;
import fr.umontpellier.iut.discordbot.lib.DeleteLogFormatter;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.utils.TimeFormat;

public class MessageDeleteListener extends AbstractEventListener {
	public MessageDeleteListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onMessageDelete(MessageDeleteEvent event) {
		boolean isGuildEvent = event.isFromGuild();

		if (isGuildEvent) {
			Bot bot = getBot();
			String logChannelId = bot.getConfig().get().getMessageDeleteChannelId();
			boolean hasLogChannel = logChannelId != null && !logChannelId.isBlank();

			if (hasLogChannel) {
				boolean isLogChannel = event.getChannel().getId().equals(logChannelId);

				if (!isLogChannel) {
					long messageId = event.getMessageIdLong();
					CachedMessage cached = bot.getMessageCacheService().getMessage(messageId);

					String channelMention = event.getChannel().getAsMention();
					String deletedAt = TimeFormat.DATE_TIME_SHORT.now().toString();
					String details = "";

					boolean isMessageCached = cached != null;

					if (isMessageCached) {
						logger.info("Message {} from {} deleted in \"{}\"", messageId, cached.getAuthorId(),
								event.getChannel().getName());
						details = String.format("Auteur: <@%d>\nSalon: %s\nEnvoyé: %s\nSupprimé: %s\n\n%s",
								cached.getAuthorId(),
								channelMention,
								TimeFormat.DATE_TIME_SHORT.format(Instant.ofEpochMilli(cached.getTimestamp())),
								deletedAt,
								DeleteLogFormatter.quote(cached.getContent()));
					} else {
						logger.info("Uncached message {} deleted in \"{}\"", messageId, event.getChannel().getName());
						details = String.format(
								"Salon: %s\nSupprimé: %s\n\n*Contenu inconnu : message absent du cache.*",
								channelMention,
								deletedAt);
					}

					bot.getLogSender().sendComponentToChannelId(logChannelId,
							Container.of(
									TextDisplay.of("# 🗑️ Message supprimé"),
									Separator.createDivider(Separator.Spacing.SMALL),
									TextDisplay.of(
											DeleteLogFormatter.truncate(details, DeleteLogFormatter.MAX_TEXT_LENGTH)))
									.withAccentColor(0xFF8800));
				}
			} else {
				logger.warn("No message delete log channel configured; skipping Discord log message");
			}
		}
	}
}