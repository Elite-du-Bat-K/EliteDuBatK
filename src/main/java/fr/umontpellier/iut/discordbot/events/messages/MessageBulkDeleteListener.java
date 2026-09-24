package fr.umontpellier.iut.discordbot.events.messages;

import java.time.Instant;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;
import fr.umontpellier.iut.discordbot.lib.DeleteLogFormatter;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.utils.TimeFormat;

public class MessageBulkDeleteListener extends AbstractEventListener {
	public MessageBulkDeleteListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		Bot bot = getBot();
		String logChannelId = bot.getConfig().get().getMessageDeleteChannelId();
		boolean hasLogChannel = logChannelId != null && !logChannelId.isBlank();

		if (hasLogChannel) {
			boolean isLogChannel = event.getChannel().getId().equals(logChannelId);

			if (!isLogChannel) {
				StringBuilder messages = new StringBuilder();
				int uncached = 0;

				for (String messageIdString : event.getMessageIds()) {
					long messageId = Long.parseLong(messageIdString);
					CachedMessage cached = bot.getMessageCacheService().getMessage(messageId);
					boolean isMessageCached = cached != null;

					if (isMessageCached) {
						messages.append(String.format("\n\n<@%d> (%s)\n%s",
								cached.getAuthorId(),
								TimeFormat.DATE_TIME_SHORT.format(Instant.ofEpochMilli(cached.getTimestamp())),
								DeleteLogFormatter.quote(cached.getContent())));
					} else {
						uncached++;
					}
				}

				logger.info("{} messages bulk deleted in \"{}\"", event.getMessageIds().size(),
						event.getChannel().getName());

				String details = String.format("Salon: %s\nSupprimé: %s\nMessages: %d (dont %d absents du cache)%s",
						event.getChannel().getAsMention(),
						TimeFormat.DATE_TIME_SHORT.now().toString(),
						event.getMessageIds().size(),
						uncached,
						messages.toString());

				bot.getLogSender().sendComponentToChannelId(logChannelId,
						Container.of(
								TextDisplay.of("# 🗑️ Suppression en masse"),
								Separator.createDivider(Separator.Spacing.SMALL),
								TextDisplay
										.of(DeleteLogFormatter.truncate(details, DeleteLogFormatter.MAX_TEXT_LENGTH)))
								.withAccentColor(0xFF8800));
			}
		} else {
			logger.warn("No message delete log channel configured; skipping Discord log message");
		}
	}
}