package fr.umontpellier.iut.discordbot.commands.study;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.SharedBot;
import fr.umontpellier.iut.discordbot.studysuite.GroupHierarchy;
import fr.umontpellier.iut.discordbot.studysuite.MemberGroups;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteClient;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteException;
import fr.umontpellier.iut.discordbot.studysuite.model.GroupRef;
import fr.umontpellier.iut.discordbot.studysuite.model.StudyGroup;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteNotLinkedException;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteRefusedException;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Une sous-commande de {@code /study}. Elles ne sont pas découvertes par réflexion comme les commandes : c'est
 * {@link StudyCommand} qui les liste.
 */
public abstract class StudySubcommand extends SharedBot {
    /** Les appels à StudySuite sont bloquants : ils ne doivent pas occuper le thread d'événements de JDA. */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected StudySubcommand(@NotNull Bot bot) {
        super(bot);
    }

    public abstract @NotNull SubcommandData getData();

    public abstract void execute(SlashCommandInteractionEvent event);

    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        event.replyChoices(List.of()).queue();
    }

    public String getName() {
        return getData().getName();
    }

    /**
     * Le groupe demandé ({@code groupInput}, identifiant ou nom), ou à défaut la classe du membre d'après ses rôles.
     */
    protected List<GroupRef> resolveGroups(String groupInput, Member member, GroupHierarchy hierarchy, StudySuiteClient client) {
        if (groupInput != null && !groupInput.isBlank()) {
            StudyGroup group = hierarchy.find(groupInput)
                    .orElseThrow(() -> new UserFacingException("Je ne connais pas le groupe « " + groupInput + " »."));
            return List.of(group.asRef());
        }

        if (member == null) {
            throw new UserFacingException("Précise un groupe avec l'option `groupe`.");
        }
        if (!client.hasApiKey()) {
            throw new UserFacingException("Précise un groupe avec l'option `groupe` : je ne peux pas encore lire ta classe depuis tes rôles.");
        }

        List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();
        Set<String> ids = MemberGroups.resolve(roleIds, client.getRoleMappings(member.getGuild().getId()), hierarchy);
        if (ids.isEmpty()) {
            throw new UserFacingException("Aucun de tes rôles n'est associé à une classe sur StudySuite. Précise un groupe avec l'option `groupe`.");
        }
        return ids.stream()
                .map(id -> hierarchy.get(id).map(StudyGroup::asRef).orElse(new GroupRef(id, id, null)))
                .toList();
    }

    /** Propose les groupes visibles dont le nom contient ce qui est tapé. */
    protected void autocompleteGroup(CommandAutoCompleteInteractionEvent event) {
        String input = event.getFocusedOption().getValue();
        async(() -> event.replyChoices(
                getBot().getStudySuite().getHierarchy().search(input).stream()
                        .limit(OptionData.MAX_CHOICES)
                        .map(g -> new Command.Choice(choiceName(g), g.id()))
                        .toList()
        ).queue());
    }

    /** « BUT1 (BUT 1A PROMO) » quand le nom d'affichage cache celui du planning. */
    private static String choiceName(StudyGroup group) {
        String label = group.label();
        return label.equals(group.internalName()) ? label : label + " (" + group.internalName() + ")";
    }

    /** Une erreur à montrer telle quelle, à la seule personne qui a lancé la commande. */
    protected static class UserFacingException extends RuntimeException {
        public UserFacingException(String message) {
            super(message);
        }
    }

    /**
     * Répond plus tard : l'interaction est différée (Discord n'attend que 3 s), puis {@code task} tourne hors du
     * thread de JDA.
     * <p>
     * Une {@link UserFacingException} ou une panne de StudySuite remplace la réponse par un message que seul
     * l'auteur voit, même si la réponse prévue était publique.
     */
    protected void replyLater(SlashCommandInteractionEvent event, boolean ephemeral, EmbedTask task) {
        replyLaterWith(event, ephemeral, () -> MessageEditData.fromEmbeds(task.run()));
    }

    /** Comme {@link #replyLater}, pour une réponse qui porte aussi des composants (menus, boutons). */
    protected void replyLaterWith(SlashCommandInteractionEvent event, boolean ephemeral, MessageTask task) {
        event.deferReply(ephemeral).queue(hook -> EXECUTOR.execute(() -> {
            try {
                hook.editOriginal(task.run()).queue();
            } catch (RuntimeException e) {
                replyError(hook, ephemeral, errorMessage(e));
            }
        }));
    }

    /** Un bouton de cette sous-commande ({@code study:<sous-commande>:…}). */
    public void onButton(ButtonInteractionEvent event) {
        event.reply("Ce bouton n'est plus actif.").setEphemeral(true).queue();
    }

    /** Un menu déroulant de cette sous-commande ({@code study:<sous-commande>:…}). */
    public void onStringSelect(StringSelectInteractionEvent event) {
        event.reply("Ce menu n'est plus actif.").setEphemeral(true).queue();
    }

    /**
     * Met à jour le message du composant : l'interaction est acquittée tout de suite, {@code task} tourne hors du thread
     * de JDA, et une erreur est signalée à part, sans toucher au message.
     */
    protected void editLater(GenericComponentInteractionCreateEvent event, MessageTask task) {
        event.deferEdit().queue(hook -> EXECUTOR.execute(() -> {
            try {
                hook.editOriginal(task.run()).queue();
            } catch (RuntimeException e) {
                hook.sendMessage("❌ " + errorMessage(e)).setEphemeral(true).queue();
            }
        }));
    }

    /** Répond en privé à celui qui a cliqué, sans toucher au message du composant. */
    protected void replyPrivatelyLater(GenericComponentInteractionCreateEvent event, MessageTask task) {
        event.deferReply(true).queue(hook -> EXECUTOR.execute(() -> {
            try {
                hook.editOriginal(task.run()).queue();
            } catch (RuntimeException e) {
                hook.editOriginal("❌ " + errorMessage(e)).queue();
            }
        }));
    }

    /** Ce qu'on dit à l'utilisateur quand {@code e} interrompt sa commande. */
    protected String errorMessage(RuntimeException e) {
        String site = getBot().getStudySuite().getBaseUrl();
        return switch (e) {
            case UserFacingException u -> u.getMessage();
            case StudySuiteNotLinkedException ignored ->
                    "Ton compte Discord n'est pas encore lié à StudySuite : connecte-toi une fois avec Discord sur "
                            + site + "/login, puis réessaie.";
            case StudySuiteRefusedException r -> refusalMessage(r);
            case StudySuiteException s -> {
                logger.warn("StudySuite call failed: {}", s.getMessage());
                yield "StudySuite ne répond pas pour l'instant, réessaie dans un moment.";
            }
            default -> {
                logger.error("Unexpected error in /study {}", getName(), e);
                yield "Une erreur inattendue est survenue.";
            }
        };
    }

    /** Les refus de l'API sont en anglais : on traduit ceux qu'on connaît. */
    private static String refusalMessage(StudySuiteRefusedException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.equals("Account not approved")) {
            return "Ton compte StudySuite n'est pas encore validé par un admin.";
        }
        if (message.startsWith("Access denied")) {
            return "Ton compte StudySuite n'a pas accès à ce groupe.";
        }
        if ("NOT_FOUND".equals(e.getCode())) {
            return "Introuvable sur StudySuite (supprimé entre-temps ?).";
        }
        return "StudySuite a refusé : " + message;
    }

    /** Lance {@code task} hors du thread de JDA (autocomplétion qui a besoin de StudySuite). */
    protected void async(Runnable task) {
        EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.warn("Background task of /study {} failed: {}", getName(), e.getMessage());
            }
        });
    }

    private void replyError(InteractionHook hook, boolean ephemeral, String message) {
        if (ephemeral) {
            hook.editOriginal("❌ " + message).queue();
            return;
        }
        // Le message différé est public : on le retire pour répondre en privé.
        hook.deleteOriginal().queue(ok -> hook.sendMessage("❌ " + message).setEphemeral(true).queue());
    }

    @FunctionalInterface
    protected interface EmbedTask {
        MessageEmbed run();
    }

    @FunctionalInterface
    protected interface MessageTask {
        MessageEditData run();
    }
}
