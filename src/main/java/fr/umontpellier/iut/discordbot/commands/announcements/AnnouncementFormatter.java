package fr.umontpellier.iut.discordbot.commands.announcements;

import fr.umontpellier.iut.discordbot.lib.Utils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Met les annonces d'enseignants en forme, en composants V2 comme les annonces de devoirs : un conteneur signé par
 * l'auteur (nom et avatar), les rôles mentionnés, et la liste des classes en pied.
 */
public final class AnnouncementFormatter {
	public static final Color COLOR = new Color(0x2E7D32);

	/**
	 * Discord limite le texte d'un message V2 à 4000 caractères en tout : on garde de la marge pour le nom, les
	 * mentions et le pied.
	 */
	public static final int MAX_MESSAGE_LENGTH = 3500;

	private AnnouncementFormatter() {
	}

	/**
	 * L'annonce telle qu'elle part dans un salon. Avec {@code actions}, c'est l'aperçu, et les boutons sont au bas du
	 * conteneur.
	 *
	 * @param mentions les mentions des rôles (voir {@link #mentions}), ou vide pour n'en mettre aucune
	 */
	public static Container announcement(String authorName, String authorAvatarUrl, String message, String mentions,
			List<String> classes, Instant sentAt, @Nullable ActionRow actions) {
		List<ContainerChildComponent> children = new ArrayList<>();
		children.add(Section.of(Thumbnail.fromUrl(authorAvatarUrl), TextDisplay.of("**" + authorName + "**\n" + message)));
		children.add(Separator.createDivider(Separator.Spacing.SMALL));

		if (!mentions.isBlank()) {
			children.add(TextDisplay.of(mentions));
		}

		children.add(TextDisplay.of("-# Annonce pour " + Utils.joinWithLastDifferent(", ", " et ", classes) + " · "
				+ TimeFormat.DATE_TIME_SHORT.format(sentAt)));

		if (actions != null) {
			children.add(actions);
		}

		return Container.of(children).withAccentColor(COLOR);
	}

	/** Les mentions de rôles, séparées par des espaces. Vide sans rôle. */
	public static String mentions(List<String> roleIds) {
		return roleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
	}

	public static TextDisplay previewHeader() {
		return TextDisplay.of("👀 **Aperçu** : vérifie ton annonce, elle n'est pas encore envoyée.");
	}

	/**
	 * @param sent   les salons où l'annonce est partie, en mention ({@code <#id>})
	 * @param failed les classes en échec, avec la raison
	 */
	public static String summary(List<String> sent, List<String> failed) {
		String result = "❌ L'annonce n'a été envoyée nulle part.";
		boolean hasSent = !sent.isEmpty();

		if (hasSent) {
			result = "✅ Annonce envoyée dans " + Utils.joinWithLastDifferent(", ", " et ", sent) + ".";
		}

		if (!failed.isEmpty()) {
			result += "\n" + failed.stream().map(failure -> "❌ " + failure).collect(Collectors.joining("\n"));
		}

		return result;
	}
}
