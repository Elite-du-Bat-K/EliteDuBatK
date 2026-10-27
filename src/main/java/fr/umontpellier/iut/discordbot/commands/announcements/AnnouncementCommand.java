package fr.umontpellier.iut.discordbot.commands.announcements;

import fr.umontpellier.iut.discordbot.Bot;
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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /annonce} : un enseignant envoie un message dans les salons d'annonces de classes.
 * <p>
 * La commande ouvre un formulaire (les classes, le message), puis montre un aperçu privé avec deux boutons
 * ({@code annonce:<send|cancel>:<id>}). Une classe est un rôle qui a un salon {@code <rôle>│annonces} (le nom que
 * créent les migrations) : l'annonce part dans ce salon et mentionne ce rôle. Les annonces en attente sont en
 * mémoire : après un redémarrage, l'aperçu expire.
 */
public class AnnouncementCommand extends AbstractCommand {
	private static final String NAME = "annonce";
	private static final String MODAL_ID = NAME + ":compose";
	private static final String FIELD_CLASSES = "classes";
	private static final String FIELD_MESSAGE = "message";
	private static final String SEND = "send";
	private static final String CANCEL = "cancel";
	private static final int MAX_PENDING = 100;
	private static final String CHANNEL_SUFFIX = "\u2502annonces";

	private record Pending(String authorId, List<String> roleIds, String message) {
	}

	private record SchoolClass(Role role, TextChannel channel) {
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
		List<SchoolClass> classes = selectableClasses(event.getGuild());
		String refusal = "Cette commande ne s'utilise que sur le serveur.";
		boolean isMember = member != null;
		boolean isAllowed = isMember && isTeacher(member);
		boolean canCompose = isAllowed && !classes.isEmpty();

		if (isMember && !isAllowed) {
			refusal = "Cette commande est réservée aux enseignants.";
		}

		if (isAllowed && classes.isEmpty()) {
			refusal = "Aucun salon d'annonces trouvé (un salon « <rôle>" + CHANNEL_SUFFIX + " » par classe).";
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
		List<SchoolClass> classes = hasValues ? stillValid(event.getGuild(), classesValue.getAsStringList()) : List.of();
		String message = hasValues ? messageValue.getAsString().strip() : "";
		boolean isValid = isAllowed && !classes.isEmpty() && !message.isBlank();

		if (isValid) {
			String id = UUID.randomUUID().toString().replace("-", "");
			List<String> roleIds = classes.stream().map(c -> c.role().getId()).toList();
			pending.put(id, new Pending(member.getId(), roleIds, message));

			ActionRow actions = ActionRow.of(
					Button.success(NAME + ":" + SEND + ":" + id, "Envoyer"),
					Button.secondary(NAME + ":" + CANCEL + ":" + id, "Annuler"));

			event.reply(new MessageCreateBuilder()
							.useComponentsV2()
							.setComponents(AnnouncementFormatter.previewHeader(), AnnouncementFormatter.announcement(
									member.getEffectiveName(), member.getEffectiveAvatarUrl(), message,
									AnnouncementFormatter.mentions(roleIds), names(classes), Instant.now(), actions))
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
		List<SchoolClass> classes = stillValid(guild, announcement.roleIds());
		List<String> names = names(classes);
		int missing = announcement.roleIds().size() - classes.size();
		List<CompletableFuture<Delivery>> deliveries = new ArrayList<>();

		// Rôle supprimé ou salon renommé depuis l'aperçu.
		if (missing > 0) {
			deliveries.add(CompletableFuture.completedFuture(
					new Delivery(null, missing + " classe(s) sans salon d'annonces")));
		}

		for (SchoolClass schoolClass : classes) {
			String roleId = schoolClass.role().getId();
			TextChannel channel = schoolClass.channel();
			CompletableFuture<Delivery> delivery = channel.sendMessage(new MessageCreateBuilder()
							.useComponentsV2()
							.setComponents(AnnouncementFormatter.announcement(author.getEffectiveName(),
									author.getEffectiveAvatarUrl(), announcement.message(),
									AnnouncementFormatter.mentions(List.of(roleId)), names, now, null))
							// Seul le rôle de la classe est notifié : un @everyone dans le texte ne prévient personne.
							.setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
							.mentionRoles(roleId)
							.build())
					.submit()
					.handle((message, error) -> {
						Delivery result = new Delivery(channel.getAsMention(), null);

						if (error != null) {
							logger.warn("Announcement to {} failed", schoolClass.role().getName(), error);
							result = new Delivery(null, schoolClass.role().getName()
									+ " : le bot n'a pas pu écrire dans " + channel.getAsMention());
						}

						return result;
					});

			deliveries.add(delivery);
		}

		return CompletableFuture.allOf(deliveries.toArray(new CompletableFuture[0]))
				.thenApply(ignored -> deliveries.stream().map(CompletableFuture::join).toList());
	}

	private Modal composeModal(List<SchoolClass> classes) {
		StringSelectMenu.Builder menu = StringSelectMenu.create(FIELD_CLASSES)
				.setPlaceholder("Une ou plusieurs classes")
				.setRequiredRange(1, classes.size());
		classes.forEach(c -> menu.addOption(c.role().getName(), c.role().getId()));

		TextInput message = TextInput.create(FIELD_MESSAGE, TextInputStyle.PARAGRAPH)
				.setPlaceholder("Le message à transmettre (markdown accepté)")
				.setMaxLength(AnnouncementFormatter.MAX_MESSAGE_LENGTH)
				.build();

		return Modal.create(MODAL_ID, "Nouvelle annonce")
				.addComponents(Label.of("Classes", menu.build()), Label.of("Message", message))
				.build();
	}

	/** Les classes du serveur, triées par nom. Discord limite un menu à 25 choix. */
	private List<SchoolClass> selectableClasses(@Nullable Guild guild) {
		List<SchoolClass> classes = new ArrayList<>();

		if (guild != null) {
			for (Role role : guild.getRoles()) {
				TextChannel channel = announcementChannel(guild, role);

				if (channel != null) {
					classes.add(new SchoolClass(role, channel));
				}
			}
		}

		classes.sort(Comparator.comparing(c -> c.role().getName(), String.CASE_INSENSITIVE_ORDER));

		if (classes.size() > StringSelectMenu.OPTIONS_MAX_AMOUNT) {
			logger.warn("{} announcement channels found, only the first {} can be selected", classes.size(),
					StringSelectMenu.OPTIONS_MAX_AMOUNT);
			classes = classes.subList(0, StringSelectMenu.OPTIONS_MAX_AMOUNT);
		}

		return classes;
	}

	/** Le rôle ou son salon ont pu disparaître entre l'ouverture du formulaire et l'envoi. */
	private List<SchoolClass> stillValid(@Nullable Guild guild, List<String> roleIds) {
		List<SchoolClass> classes = new ArrayList<>();

		if (guild != null) {
			for (String roleId : roleIds) {
				Role role = guild.getRoleById(roleId);
				TextChannel channel = role == null ? null : announcementChannel(guild, role);

				if (channel != null) {
					classes.add(new SchoolClass(role, channel));
				}
			}
		}

		return classes;
	}

	@Nullable
	private static TextChannel announcementChannel(Guild guild, Role role) {
		List<TextChannel> channels = guild.getTextChannelsByName(channelName(role.getName()), true);
		return channels.isEmpty() ? null : channels.getFirst();
	}

	/** Le nom que créent les migrations : le rôle en minuscules, puis {@code │annonces} (U+2502, pas un pipe). */
	static String channelName(String roleName) {
		return roleName.toLowerCase() + CHANNEL_SUFFIX;
	}

	private static List<String> names(List<SchoolClass> classes) {
		return classes.stream().map(c -> c.role().getName()).toList();
	}
}
