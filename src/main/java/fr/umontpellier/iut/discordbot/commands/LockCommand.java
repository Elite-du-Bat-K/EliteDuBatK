package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.dataobjects.ChannelLock;
import fr.umontpellier.iut.discordbot.database.repositories.ChannelLockRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import fr.umontpellier.iut.discordbot.services.ChannelLockService;
import fr.umontpellier.iut.discordbot.services.exceptions.ServiceException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public class LockCommand extends AbstractCommand {
    private final ChannelLockService channelLockService;

    public LockCommand(Bot bot) {
        super(bot);
        this.channelLockService = new ChannelLockService(
                getBot().getRepositories().getRepository(ChannelLockRepository.class)
        );
    }

    @NotNull
    @Override
    public SlashCommandData getCommandInformation() {
        return Commands.slash("lock", "Bloquer les interactions avec ce salon").addSubcommands(
                new SubcommandData("on", "Activer le verrouillage du salon"),
                new SubcommandData("off", "Désactiver le verrouillage du salon"),
                new SubcommandData("status", "Obtenir l'état du verrouillage du salon")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = Objects.requireNonNull(event.getSubcommandName());

        switch (subcommand) {
            case "on" -> handleLockOn(event);
            case "off" -> handleLockOff(event);
            case "status" -> handleStatus(event);
            default -> event.reply("Commande inconnue").setEphemeral(true).queue();
        }
    }

    private void handleLockOn(SlashCommandInteractionEvent event) {
        Optional<Guild> guildOpt = getGuild(event);
        Optional<Member> memberOpt = getMember(event);

        if (guildOpt.isEmpty() || memberOpt.isEmpty()) {
            event.reply("Erreur: impossible de récupérer la guilde ou le membre.").setEphemeral(true).queue();
            return;
        }

        try {
            TextChannel channel = event.getChannel().asTextChannel();

            Guild guild = guildOpt.get();
            Member member = memberOpt.get();

            channelLockService.lockChannel(channel, guild, member);
            event.reply("✅ Le salon a été verrouillé par " + member.getAsMention()).queue();
        } catch (ServiceException e) {
            logger.warn("Failed to lock channel: {}", e.getMessage());
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleLockOff(SlashCommandInteractionEvent event) {
        try {
            TextChannel channel = event.getChannel().asTextChannel();
            channelLockService.unlockChannel(channel);
            event.reply("✅ Le salon a été déverrouillé. L'état précédent a été restauré.").queue();
        } catch (ServiceException e) {
            logger.warn("Failed to unlock channel: {}", e.getMessage());
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        try {
            TextChannel channel = event.getChannel().asTextChannel();

            Optional<ChannelLock> lockOpt = channelLockService.getChannelLockByChannelId(channel.getId());
            if (lockOpt.isEmpty()) {
                event.reply("ℹ️ Ce salon n'est pas verrouillé.").setEphemeral(true).queue();
                return;
            }

            ChannelLock lock = lockOpt.get();
            String lockedByUser = getBot().getJda().retrieveUserById(lock.getLockedById())
                    .complete()
                    .getName();

            String statusMessage = """
                    🔒 Ce salon est actuellement verrouillé
                    Verrouillé par: **%s**
                    Date du verrouillage: <t:%d:F>""".formatted(
                    lockedByUser,
                    lock.getLockedAt() / 1000
            );

            event.reply(statusMessage).setEphemeral(true).queue();
        } catch (ServiceException e) {
            logger.warn("Failed to get lock status: {}", e.getMessage());
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        }
    }
}
