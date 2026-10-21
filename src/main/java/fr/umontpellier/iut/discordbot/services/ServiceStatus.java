package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.config.ConfigStructure.StatusTarget;

/**
 * Le résultat d'une vérification de {@link ServiceStatusChecker}.
 *
 * @param httpCode      le code HTTP reçu, ou 0 sans réponse
 * @param latencyMillis le temps de réponse, ou 0 sans réponse
 */
public record ServiceStatus(StatusTarget target, State state, int httpCode, long latencyMillis) {
	public enum State {
		/** Le service a répondu sans erreur serveur (une redirection vers la page de connexion compte). */
		UP,
		/** Le service a répondu, mais avec une erreur serveur (5xx). */
		DOWN,
		/** Pas de réponse : délai dépassé, DNS ou connexion refusée. */
		UNREACHABLE,
		/** L'URL de la config n'est pas valide, rien n'a été envoyé. */
		INVALID_URL
	}

	private static final int FIRST_SERVER_ERROR = 500;

	public static ServiceStatus fromResponse(StatusTarget target, int httpCode, long latencyMillis) {
		State state = State.DOWN;
		boolean isUp = httpCode < FIRST_SERVER_ERROR;

		if (isUp) {
			state = State.UP;
		}

		return new ServiceStatus(target, state, httpCode, latencyMillis);
	}

	public boolean isUp() {
		return state == State.UP;
	}

	public static ServiceStatus unreachable(StatusTarget target) {
		return new ServiceStatus(target, State.UNREACHABLE, 0, 0);
	}

	public static ServiceStatus invalidUrl(StatusTarget target) {
		return new ServiceStatus(target, State.INVALID_URL, 0, 0);
	}
}
