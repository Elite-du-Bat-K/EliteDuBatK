package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class VoiceUpdateEventListener extends AbstractEventListener {
    public VoiceUpdateEventListener(Bot bot) {
        super(bot);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() != null) {
            onGuildVoiceJoin(event);
        } else if (event.getChannelLeft() != null) {
            onGuildVoiceLeave(event);
        }
    }

    private void onGuildVoiceJoin(@NotNull GuildVoiceUpdateEvent event) {
        Channel channel = Objects.requireNonNull(event.getChannelJoined());
        logger.info("\"{}\" has joined voice channel \"{}\"", event.getMember().getEffectiveName(), channel.getName());
    }

    private void onGuildVoiceLeave(@NotNull GuildVoiceUpdateEvent event) {
        Channel channel = Objects.requireNonNull(event.getChannelLeft());
        logger.info("\"{}\" has left voice channel \"{}\"", event.getMember().getEffectiveName(), channel.getName());
    }
}
