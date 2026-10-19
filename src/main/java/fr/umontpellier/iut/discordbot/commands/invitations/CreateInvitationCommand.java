package fr.umontpellier.iut.discordbot.commands.invitations;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.lib.QrCodeService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IInviteContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class CreateInvitationCommand extends AbstractCommand {

	public CreateInvitationCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("inviter", "Créer une invitation (possibilité de transmettre le lien par mp)")
				.addOption(OptionType.CHANNEL, "channel", "Salon ciblé par l'invitation", true, false)
				.addOption(OptionType.INTEGER, "duration", "Durée de validité de l'invitation (0 pour éternelle)", true,
						false)
				.addOption(OptionType.INTEGER, "uselimit", "Limite d'utilisation du lien (0 pour illimité)", true,
						false)
				.addOption(OptionType.BOOLEAN, "qrcode", "Générer un QR Code de l'invitation", true, false)
				.addOption(OptionType.USER, "sendto", "Destinataire du lien", false, false)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.CREATE_INSTANT_INVITE));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		OptionMapping sendToMapping = event.getOption("sendto");
		boolean isEphemeral = sendToMapping != null;

		// Acquittement immédiat pour éviter le timeout de 3 secondes (Erreur 10062)
		event.deferReply(isEphemeral).queue();

		CompletableFuture.runAsync(() -> {
			String replyMessage = "Une erreur inattendue est survenue.";
			FileUpload replyFileUpload = null;

			Member member = event.getMember();
			Guild guild = event.getGuild();

			boolean hasPermission = member != null && member.hasPermission(Permission.CREATE_INSTANT_INVITE);
			boolean hasGuild = guild != null;

			if (!hasPermission) {
				replyMessage = "Tu n'as pas la permission d'exécuter cette commande.";
			}

			if (hasPermission && !hasGuild) {
				replyMessage = "Serveur introuvable.";
			}

			if (hasPermission && hasGuild) {
				OptionMapping channelMapping = event.getOption("channel");
				OptionMapping durationMapping = event.getOption("duration");
				OptionMapping useLimitMapping = event.getOption("uselimit");
				OptionMapping qrCodeMapping = event.getOption("qrcode");

				boolean hasRequiredOptions = channelMapping != null && durationMapping != null
						&& useLimitMapping != null && qrCodeMapping != null;

				if (!hasRequiredOptions) {
					replyMessage = "Paramètres requis manquants.";
				}

				if (hasRequiredOptions) {
					GuildChannel guildChannel = channelMapping.getAsChannel();
					boolean isInviteContainer = guildChannel instanceof IInviteContainer;

					if (!isInviteContainer) {
						replyMessage = "Le salon sélectionné ne supporte pas la création d'invitations.";
					}

					if (isInviteContainer) {
						IInviteContainer inviteContainer = (IInviteContainer) guildChannel;
						int duration = durationMapping.getAsInt();
						int useLimit = useLimitMapping.getAsInt();
						boolean qrCode = qrCodeMapping.getAsBoolean();
						User sendToUser = sendToMapping != null ? sendToMapping.getAsUser() : null;

						String inviteUrl = "";
						boolean inviteCreated = false;

						try {
							Invite invite = inviteContainer.createInvite()
									.setMaxAge(duration)
									.setMaxUses(useLimit)
									.complete();

							if (invite != null) {
								inviteUrl = invite.getUrl();
								inviteCreated = true;
							}
						} catch (Exception exception) {
							logger.error("API Error during invite creation", exception);
							replyMessage = "Impossible de créer l'invitation suite à une erreur de l'API Discord.";
						}

						if (inviteCreated) {
							String durationText = "Illimitée";
							boolean hasDurationLimit = duration > 0;

							if (hasDurationLimit) {
								durationText = duration + " secondes";
							}

							String usesText = "Illimité";
							boolean hasUsesLimit = useLimit > 0;

							if (hasUsesLimit) {
								usesText = String.valueOf(useLimit);
							}

							String recapText = "\n\n**Récapitulatif :**\n- Durée de validité : " + durationText
									+ "\n- Nombre max d'utilisation : " + usesText;
							String finalMessage = inviteUrl + recapText;

							byte[] qrBytes = null;
							boolean qrGenerated = false;

							if (qrCode) {
								try {
									BufferedImage logo = null;
									InputStream logoStream = getClass().getResourceAsStream("/logo.png");
									boolean hasLogoStream = logoStream != null;

									if (hasLogoStream) {
										logo = ImageIO.read(logoStream);
										logoStream.close();
									}

									QrCodeService qrCodeService = new QrCodeService();
									qrBytes = qrCodeService.generateQrCodeWithLogo(inviteUrl, logo);
									qrGenerated = qrBytes != null && qrBytes.length > 0;
								} catch (Exception exception) {
									logger.error("Error generating QR code", exception);
								}
							}

							boolean hasTargetUser = sendToUser != null;

							if (hasTargetUser) {
								boolean dmSent = false;
								try {
									if (qrGenerated) {
										FileUpload dmFileUpload = FileUpload.fromData(qrBytes, "invite_qr.png");
										sendToUser.openPrivateChannel().complete().sendMessage(finalMessage)
												.addFiles(dmFileUpload).complete();
									}
									if (!qrGenerated) {
										sendToUser.openPrivateChannel().complete().sendMessage(finalMessage).complete();
									}
									dmSent = true;
								} catch (Exception exception) {
									logger.warn("Could not send DM to user {}", sendToUser.getId());
								}

								if (dmSent) {
									replyMessage = "L'invitation a été envoyée avec succès par message privé.";
								}

								if (!dmSent) {
									replyMessage = "Impossible d'envoyer un message privé au destinataire. Voici le lien :\n"
											+ finalMessage;
									if (qrGenerated) {
										replyFileUpload = FileUpload.fromData(qrBytes, "invite_qr.png");
									}
								}
							}

							if (!hasTargetUser) {
								replyMessage = finalMessage;
								if (qrGenerated) {
									replyFileUpload = FileUpload.fromData(qrBytes, "invite_qr.png");
								}
							}
						}
					}
				}
			}

			// Sortie unique avec modification de la réponse différée
			boolean hasReplyFile = replyFileUpload != null;
			if (hasReplyFile) {
				event.getHook().editOriginal(replyMessage).setFiles(replyFileUpload).queue();
			}
			if (!hasReplyFile) {
				event.getHook().editOriginal(replyMessage).queue();
			}
		});
	}
}