package fr.umontpellier.iut.discordbot.commands.announcements;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.config.ConfigStructure;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import fr.umontpellier.iut.discordbot.lib.BoundedCache;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /annonce} : un enseignant envoie un message dans les salons d'annonces de classes.
 * <p>
 * La commande ouvre un formulaire (les classes, le message), puis montre un aperçu privé avec deux boutons
 * ({@code annonce:<send|cancel>:<id>}). L'annonce part dans le salon de chaque classe ({@code announcementChannels}),
 * avec les rôles de cette classe ({@code groups}) mentionnés. Les annonces en attente sont en mémoire : après un
 * redémarrage, l'aperçu expire.
 */
public class AnnouncementCommand extends AbstractCommand {
	private static final String NAME = "annonce";
	private static final String MODAL_ID = NAME + ":compose";
	private static final String FIELD_CLASSES = "classes";
	private static final String FIELD_MESSAGE = "message";
	private static final String SEND = "send";
	private static final String CANCEL = "cancel";
	private static final int MAX_PENDING = 100;

	private record Pending(String authorId, List<String> classes, String message) {
	}

	private record Delivery(@Nullable String sentChannel, @Nullable String failure) {
	}

	private final Map<String, Pending> pending = Collections.synchronizedMap(new BoundedCache<>(MAX_PENDING));

	public AnnouncementCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash(NAME, "Envoyer une annonce dans les salons de classes (enseignants)")
				.setContexts(InteractionContextType.GUILD);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Member member = event.getMember();
		List<String> classes = selectableClasses();
		String refusal = "Cette commande ne s'utilise que sur le serveur.";
		boolean isMember = member != null;
		boolean isAllowed = isMember && isTeacher(member);
		boolean canCompose = isAllowed && !classes.isEmpty();

		if (isMember && !isAllowed) {
			refusal = "Cette commande est réservée aux enseignants.";
		}

		if (isAllowed && classes.isEmpty()) {
			refusal = "Aucun salon d'annonces n'est configuré.";
		}

		if (canCompose) {
			event.replyModal(composeModal(classes)).queue();
		}

		if (!canCompose) {
			event.reply(refusal).setEphemeral(true).queue();
		}
	}

	@Override
	public void onModal(ModalInteractionEvent event) {
		Member member = event.getMember();
		ModalMapping classesValue = event.getValue(FIELD_CLASSES);
		ModalMapping messageValue = event.getValue(FIELD_MESSAGE);
		boolean isAllowed = member != null && isTeacher(member);
		boolean hasValues = classesValue != null && messageValue != null;
		List<String> classes = hasValues ? configuredOnly(classesValue.getAsStringList()) : List.of();
		String message = hasValues ? messageValue.getAsString().strip() : "";
		boolean isValid = isAllowed && !classes.isEmpty() && !message.isBlank();

		if (isValid) {
			String id = UUID.randomUUID().toString().replace("-", "");
			pending.put(id, new Pending(member.getId(), classes, message));

			ActionRow actions = ActionRow.of(
					Button.success(NAME + ":" + SEND + ":" + id, "Envoyer"),
					Button.secondary(NAME + ":" + CANCEL + ":" + id, "Annuler"));
			List<String> roleIds = classes.stream().flatMap(c -> config().getRolesIdForGroup(c).stream()).toList();

			event.reply(new MessageCreateBuilder()
							.useComponentsV2()
							.setComponents(AnnouncementFormatter.previewHeader(), AnnouncementFormatter.announcement(
									member.getEffectiveName(), member.getEffectiveAvatarUrl(), message,
									AnnouncementFormatter.mentions(roleIds), classes, Instant.now(), actions))
							// L'aperçu montre les mentions sans prévenir personne.
							.setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
							.build())
					.setEphemeral(true)
					.queue();
		}

		if (!isValid) {
			event.reply("❌ Annonce vide ou formulaire invalide, relance /" + NAME + ".").setEphemeral(true).queue();
		}
	}

	@Override
	public void onButton(ButtonInteractionEvent event) {
		String[] parts = event.getComponentId().split(":");
		Pending announcement = parts.length == 3 ? pending.get(parts[2]) : null;
		Member member = event.getMember();
		boolean isAuthor = announcement != null && member != null && announcement.authorId().equals(member.getId());

		if (announcement == null) {
			event.editMessage(text("⌛ Cet aperçu a expiré, relance /" + NAME + ".")).queue();
		}

		if (announcement != null && !isAuthor) {
			event.reply("Cette annonce n'est pas la tienne.").setEphemeral(true).queue();
		}

		if (isAuthor && parts[1].equals(CANCEL)) {
			pending.remove(parts[2]);
			event.editMessage(text("Annonce annulée.")).queue();
		}

		if (isAuthor && parts[1].equals(SEND)) {
			pending.remove(parts[2]);
			event.editMessage(text("⏳ Envoi en cours…")).queue();
			deliver(event.getGuild(), member, announcement).thenAccept(deliveries -> {
				List<String> sent = deliveries.stream().map(Delivery::sentChannel).filter(c -> c != null).toList();
				List<String> failed = deliveries.stream().map(Delivery::failure).filter(f -> f != null).toList();
				event.getHook().editOriginal(text(AnnouncementFormatter.summary(sent, failed))).queue();
			});
		}
	}

	/** L'aperçu est un message V2 : il le reste, et ses modifications doivent l'être aussi. */
	static MessageEditData text(String content) {
		return new MessageEditBuilder().useComponentsV2().setComponents(TextDisplay.of(content)).build();
	}

	private CompletableFuture<List<Delivery>> deliver(@Nullable Guild guild, Member author, Pending announcement) {
		Instant now = Instant.now();
		List<CompletableFuture<Delivery>> deliveries = new ArrayList<>();

		for (String schoolClass : announcement.classes()) {
			String channelId = config().getAnnouncementChannels().get(schoolClass);
			GuildMessageChannel channel = guild == null || channelId == null
					? null
					: guild.getChannelById(GuildMessageChannel.class, channelId);
			CompletableFuture<Delivery> delivery = CompletableFuture.completedFuture(
					new Delivery(null, schoolClass + " : salon d'annonces introuvable"));

			if (channel != null) {
				List<String> roleIds = config().getRolesIdForGroup(schoolClass);
				delivery = channel.sendMessage(new MessageCreateBuilder()
								.useComponentsV2()
								.setComponents(AnnouncementFormatter.announcement(author.getEffectiveName(),
										author.getEffectiveAvatarUrl(), announcement.message(),
										AnnouncementFormatter.mentions(roleIds), announcement.classes(), now, null))
								// Seuls les rôles de la classe sont notifiés : un @everyone dans le texte ne prévient personne.
								.setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
								.mentionRoles(roleIds)
								.build())
						.submit()
						.handle((message, error) -> {
							Delivery result = new Delivery(channel.getAsMention(), null);

							if (error != null) {
								logger.warn("Announcement to {} failed", schoolClass, error);
								result = new Delivery(null, schoolClass + " : le bot n'a pas pu écrire dans "
										+ channel.getAsMention());
							}

							return result;
						});
			}

			deliveries.add(delivery);
		}

		return CompletableFuture.allOf(deliveries.toArray(new CompletableFuture[0]))
				.thenApply(ignored -> deliveries.stream().map(CompletableFuture::join).toList());
	}

	private Modal composeModal(List<String> classes) {
		StringSelectMenu.Builder menu = StringSelectMenu.create(FIELD_CLASSES)
				.setPlaceholder("Une ou plusieurs classes")
				.setRequiredRange(1, classes.size());
		classes.forEach(schoolClass -> menu.addOption(schoolClass, schoolClass));

		TextInput message = TextInput.create(FIELD_MESSAGE, TextInputStyle.PARAGRAPH)
				.setPlaceholder("Le message à transmettre (markdown accepté)")
				.setMaxLength(AnnouncementFormatter.MAX_MESSAGE_LENGTH)
				.build();

		return Modal.create(MODAL_ID, "Nouvelle annonce")
				.addComponents(Label.of("Classes", menu.build()), Label.of("Message", message))
				.build();
	}

	/** Les classes du menu, dans l'ordre de la config. Discord limite un menu à 25 choix. */
	private List<String> selectableClasses() {
		List<String> classes = List.copyOf(config().getAnnouncementChannels().keySet());

		if (classes.size() > StringSelectMenu.OPTIONS_MAX_AMOUNT) {
			logger.warn("{} announcement channels configured, only the first {} can be selected", classes.size(),
					StringSelectMenu.OPTIONS_MAX_AMOUNT);
			classes = classes.subList(0, StringSelectMenu.OPTIONS_MAX_AMOUNT);
		}

		return classes;
	}

	/** La config a pu changer entre l'ouverture du formulaire et son envoi. */
	private List<String> configuredOnly(List<String> classes) {
		return classes.stream().filter(config().getAnnouncementChannels()::containsKey).toList();
	}

	private ConfigStructure config() {
		return getBot().getConfig().get();
	}
}
