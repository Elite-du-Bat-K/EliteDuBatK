package fr.umontpellier.iut.discordbot.events.messages;

import java.time.Instant;

import javax.annotation.Nonnull;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.config.ConfigStructure.SystemChannel;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;
import fr.umontpellier.iut.discordbot.lib.DeleteLogFormatter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.utils.TimeFormat;

public class MessageUpdateListener extends AbstractEventListener {
	private static final int MAX_CONTENT_LENGTH = 1700;

	public MessageUpdateListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onMessageUpdate(@Nonnull MessageUpdateEvent event) {
		boolean isValidSource = false;

		if (event.isFromGuild()) {
			if (!event.getAuthor().isBot()) {
				isValidSource = true;
			}
		}

		if (isValidSource) {
			Message msg = event.getMessage();
			String newContent = msg.getContentRaw();
			long messageId = msg.getIdLong();
			long channelId = msg.getChannel().getIdLong();
			long authorId = msg.getAuthor().getIdLong();
			long timestamp = msg.getTimeCreated().toInstant().toEpochMilli();

			CachedMessage previous = getBot().getMessageCacheService().getMessage(messageId);
			CachedMessage updated = new CachedMessage(messageId, channelId, authorId, newContent, timestamp);
			getBot().getMessageCacheService().cacheMessage(updated);

			boolean hasContentChanged = false;

			if (previous != null) {
				if (!previous.getContent().equals(newContent)) {
					hasContentChanged = true;
				}
			}

			if (hasContentChanged) {
				String before = "*Contenu inconnu : message absent du cache.*";

				if (previous != null) {
					before = quote(previous.getContent());
				}

				String details = String.format(
						"Auteur: %s\nSalon: %s\nEnvoyé: %s\nModifié: %s\n[Aller au message](%s)\n\n**Avant**\n%s\n\n**Après**\n%s",
						event.getAuthor().getAsMention(),
						event.getChannel().getAsMention(),
						TimeFormat.DATE_TIME_SHORT.format(msg.getTimeCreated()),
						TimeFormat.DATE_TIME_SHORT.now(),
						msg.getJumpUrl(),
						before,
						quote(newContent));

				logger.info("Message {} from {} edited in \"{}\"", messageId, authorId, event.getChannel().getName());
				getBot().getLogSender().sendLog(SystemChannel.MESSAGE_EDIT_CHANNEL, "# ✏️ Message modifié", 0xFFFF00,
						details);
			}
		}
	}

	private static String quote(String content) {
		return DeleteLogFormatter.quote(DeleteLogFormatter.truncate(content, MAX_CONTENT_LENGTH));
	}
}