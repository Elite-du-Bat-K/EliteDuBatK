package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.components.container.Container;
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

        sendVoiceLog(
                Container.of(
                        TextDisplay.of("# \uD83D\udd0A Connexion vocal"),
                        Separator.createDivider(Separator.Spacing.SMALL),
                        Section.of(
                                Thumbnail.fromUrl(event.getMember().getUser().getEffectiveAvatarUrl()),
                                TextDisplay.ofFormat("**%s** a rejoint le canal vocal **%s**", event.getMember().getAsMention(), channel.getAsMention())
                        )
                ).withAccentColor(0x00FF00)
        );
    }

    private void onGuildVoiceLeave(@NotNull GuildVoiceUpdateEvent event) {
        Channel channel = Objects.requireNonNull(event.getChannelLeft());
        logger.info("\"{}\" has left voice channel \"{}\"", event.getMember().getEffectiveName(), channel.getName());

        sendVoiceLog(
                Container.of(
                        TextDisplay.of("# \uD83D\uDD07 Connexion vocal"),
                        Separator.createDivider(Separator.Spacing.SMALL),
                        Section.of(
                                Thumbnail.fromUrl(event.getMember().getUser().getEffectiveAvatarUrl()),
                                TextDisplay.ofFormat("**%s** a quitté le canal vocal **%s**", event.getMember().getAsMention(), channel.getAsMention())
                        )
                ).withAccentColor(0xFF0000)
        );
    }

    private void sendVoiceLog(Container container) {
        String voiceChannelId = getBot().getConfig().get().getVoiceChannelId();
        if (voiceChannelId == null || voiceChannelId.isBlank()) {
            logger.warn("No voice log channel configured; skipping Discord log message");
            return;
        }

        getBot().getLogSender().sendComponentToChannelId(voiceChannelId, container);
    }
}
