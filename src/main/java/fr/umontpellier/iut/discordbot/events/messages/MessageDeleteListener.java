package fr.umontpellier.iut.discordbot.events.messages;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;

public class MessageDeleteListener extends AbstractEventListener {
	public MessageDeleteListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onMessageDelete(MessageDeleteEvent event) {
		Bot bot = getBot();

		String msgId = event.getMessageId();

		String logMessage = "";

		if (bot.getCachedMessages().containsKey(msgId)) {
			
		} else {

		}
	}
}