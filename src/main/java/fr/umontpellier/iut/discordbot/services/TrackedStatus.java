package fr.umontpellier.iut.discordbot.services;

import java.time.Instant;

/**
 * Le dernier résultat d'un service, et depuis quand il est dans cet état (en ligne ou non).
 *
 * @param since        le début de l'état actuel, ou le démarrage du bot s'il n'a pas changé depuis
 * @param sinceStartup vrai tant que l'état n'a pas changé depuis le démarrage : il dure alors « au moins » depuis
 *                     {@code since}, l'historique d'avant étant perdu au redémarrage
 */
public record TrackedStatus(ServiceStatus latest, Instant since, boolean sinceStartup, Instant checkedAt) {

	public static TrackedStatus first(ServiceStatus latest, Instant startedAt, Instant now) {
		return new TrackedStatus(latest, startedAt, true, now);
	}

	/**
	 * Passer de « en panne » à « injoignable » ne remet pas le compteur à zéro : seul le passage entre en ligne et
	 * hors ligne compte.
	 */
	public TrackedStatus next(ServiceStatus newStatus, Instant now) {
		TrackedStatus result = new TrackedStatus(newStatus, since, sinceStartup, now);
		boolean hasChanged = newStatus.isUp() != latest.isUp();

		if (hasChanged) {
			result = new TrackedStatus(newStatus, now, false, now);
		}

		return result;
	}
}
