package fr.umontpellier.iut.discordbot.events.messages;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
		Bot bot = getBot();

		Message msg = event.getMessage();

		String senderId = event.getAuthor().getId();
		String msgId = event.getMessageId();
		OffsetDateTime timeSend = msg.getTimeCreated();

		String content = msg.getContentRaw();

		bot.getCachedMessages().put(msgId, new CachedMessage(senderId, content, timeSend.atZoneSameInstant(ZoneOffset.UTC)));
	}
}
