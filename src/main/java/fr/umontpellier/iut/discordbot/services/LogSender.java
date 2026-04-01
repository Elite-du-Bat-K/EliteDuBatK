package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.SharedBot;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class LogSender extends SharedBot {
    private final Logger logger;

    public LogSender(@NotNull Bot bot) {
        super(bot);
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    private Optional<TextChannel> asSendableChannel(String channelId) {
        return Optional.ofNullable(getBot().getJda().getChannelById(TextChannel.class, channelId));
    }

    public void sendComponentsToChannelId(String channelId, Collection<? extends MessageTopLevelComponent> components) {
        asSendableChannel(channelId).ifPresentOrElse(
                chan -> chan.sendMessageComponents(components).useComponentsV2().queue(),
                () -> logger.warn("Channel with ID {} not found, cannot send log message", channelId)
        );
    }

    public void sendComponentToChannelId(String channelId, MessageTopLevelComponent component) {
        sendComponentsToChannelId(channelId, List.of(component));
    }

    public void sendTextToChannelId(String channelId, String text) {
        asSendableChannel(channelId).ifPresentOrElse(
                chan -> chan.sendMessage(text).queue(),
                () -> logger.warn("Channel with ID {} not found, cannot send log message", channelId)
        );
    }
}
