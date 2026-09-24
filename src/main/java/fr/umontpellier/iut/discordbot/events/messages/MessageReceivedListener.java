package fr.umontpellier.iut.discordbot.events.messages;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class MessageReceivedListener extends AbstractEventListener {
	public MessageReceivedListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		boolean isValidSource = false;

		if (event.isFromGuild()) {
			if (!event.getAuthor().isBot()) {
				isValidSource = true;
			}
		}

		if (isValidSource) {
			Message msg = event.getMessage();
			long messageId = msg.getIdLong();
			long channelId = msg.getChannel().getIdLong();
			long authorId = msg.getAuthor().getIdLong();
			String content = msg.getContentRaw();
			long timestamp = msg.getTimeCreated().toInstant().toEpochMilli();

			CachedMessage cachedMessage = new CachedMessage(messageId, channelId, authorId, content, timestamp);
			getBot().getMessageCacheService().cacheMessage(cachedMessage);
		}
	}
}