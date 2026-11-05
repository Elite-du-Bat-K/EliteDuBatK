package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class VerifySignatureCommand extends AbstractCommand {

	public VerifySignatureCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("verify", "Verify a cryptographic signature file")
				.addOption(OptionType.ATTACHMENT, "public_key", "The public key file (.pem)", true)
				.addOption(OptionType.ATTACHMENT, "signature", "The signature file (.sig)", true)
				.addOption(OptionType.STRING, "message", "The exact message payload to verify", true);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		event.deferReply(true).queue();

		Message.Attachment pubKeyAttachment = Objects.requireNonNull(event.getOption("public_key")).getAsAttachment();
		Message.Attachment sigAttachment = Objects.requireNonNull(event.getOption("signature")).getAsAttachment();
		String messagePayload = Objects.requireNonNull(event.getOption("message")).getAsString();

		CompletableFuture<byte[]> pubKeyFuture = this.downloadAttachment(pubKeyAttachment);
		CompletableFuture<byte[]> sigFuture = this.downloadAttachment(sigAttachment);

		CompletableFuture.allOf(pubKeyFuture, sigFuture).whenComplete((ignored, exception) -> {
			boolean hasDownloadError = exception != null;

			if (hasDownloadError) {
				event.getHook().sendMessage("Error downloading attachments.").queue();
			}

			if (!hasDownloadError) {
				byte[] pubKeyBytes = pubKeyFuture.join();
				byte[] sigBytes = sigFuture.join();
				String base64Signature = new String(sigBytes, StandardCharsets.UTF_8).trim();

				boolean isValid = this.getBot().getCryptoReceiptService().verifySignature(pubKeyBytes, base64Signature,
						messagePayload);

				if (isValid) {
					event.getHook()
							.sendMessage(
									"Signature is **VALID**. The message was signed by the owner of this public key.")
							.queue();
				}

				if (!isValid) {
					event.getHook()
							.sendMessage(
									"Signature is **INVALID**. The payload does not match, or the wrong key was used.")
							.queue();
				}
			}
		});
	}

	private CompletableFuture<byte[]> downloadAttachment(Message.Attachment attachment) {
		CompletableFuture<byte[]> outputFuture = new CompletableFuture<>();
		boolean isNotNull = attachment != null;

		if (isNotNull) {
			attachment.getProxy().download().whenComplete((inputStream, throwable) -> {
				boolean hasError = throwable != null;

				if (hasError) {
					outputFuture.completeExceptionally(throwable);
				}

				if (!hasError) {
					try {
						byte[] bytes = inputStream.readAllBytes();
						outputFuture.complete(bytes);
					} catch (Exception e) {
						outputFuture.completeExceptionally(e);
					}
				}
			});
		}

		if (!isNotNull) {
			outputFuture.complete(new byte[0]);
		}

		return outputFuture;
	}
}