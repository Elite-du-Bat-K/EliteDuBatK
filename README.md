# EliteDuBatK

### Events

Chaque Event est dans sa propre classe.
Par exemple, Une classe pour la suppression de messages n'écoutera aucun autre événement que les suppressions de messages.

## Configuration

La configuration est lue dans `config.json` (dossier courant), ou dans le fichier indiqué par la variable d'environnement `CONFIG_PATH`. Voir `example.config.json`.

```json
{
	"token": "TOKEN_DU_BOT",
	"databasePath": "data.db",
	"allowed_guild_id": "ID_DU_SERVEUR",
	"adminRole": "ID_DU_ROLE",
	"teacherRole": "ID_DU_ROLE",
	"groups": {
		"nom-du-groupe": ["ID_ROLE_1", "ID_ROLE_2"]
	},
	"announcementChannels": {
		"nom-du-groupe": "ID_DU_SALON"
	},
	"channels": {
		"voice_channel": "ID_DU_SALON",
		"message_delete_channel": "ID_DU_SALON",
		"message_edit_channel": "ID_DU_SALON",
		"lock_channel": "ID_DU_SALON",
		"channel_log_channel": "ID_DU_SALON",
		"role_log_channel": "ID_DU_SALON",
		"member_log_channel": "ID_DU_SALON",
		"moderation_channel": "ID_DU_SALON",
		"honeypot_channel": "ID_DU_SALON"
	},
	"studySuite": {
		"baseUrl": "https://study-info.umontp.fr",
		"apiKey": "CLE_DU_BOT"
	},
	"statusIntervalSeconds": 60,
	"statusTargets": [
		{ "displayName": "Moodle", "publicUrl": "https://moodle.umontpellier.fr" },
		{ "displayName": "GitLab", "publicUrl": "https://gitlabinfo.iutmontp.univ-montp2.fr", "pingUrl": "https://gitlabinfo.iutmontp.univ-montp2.fr/api/v4/version" }
	]
}
```

| Clé | Description |
|---|---|
| `token` | Token du bot (Discord Developer Portal). Ne jamais le commiter. |
| `databasePath` | Chemin du fichier SQLite. Un chemin relatif part du dossier courant du bot (voir [Docker](#docker)). |
| `allowed_guild_id` | Identifiant du serveur |
| `adminRole` | ID du rôle autorisé à utiliser `/lock`. Les membres avec la permission Administrateur y ont aussi accès. Pendant un verrouillage, ce rôle garde le droit d'écrire. |
| `teacherRole` | ID du rôle des enseignants, autorisés à utiliser `/annonce`. Les admins y ont aussi accès. |
| `groups` | Groupes de rôles pour la commande de ping : nom du groupe → liste d'IDs de rôles. `/annonce` mentionne aussi ces rôles. |
| `announcementChannels` | Salons d'annonces pour `/annonce` : nom du groupe (le même que dans `groups`) → ID du salon. 25 au plus. |
| `channels` | Salons de logs. Plusieurs clés peuvent pointer vers le même salon ; une clé absente ou vide désactive ce log (avertissement au démarrage). |
| `statusTargets` | Services vérifiés pour `/status`. `displayName` : le nom affiché ; `publicUrl` : le lien affiché ; `pingUrl` (facultatif) : l'URL testée à la place de `publicUrl`. |
| `statusIntervalSeconds` | Facultatif. Intervalle entre deux vérifications des services, en secondes (60 par défaut, 30 au minimum). |
| `studySuite` | Accès à [StudySuite](#studysuite). `baseUrl` vide : `https://study-info.umontp.fr`. `apiKey` : une des clés `bot.apiKeys` de l'API, à ne jamais commiter. |

Salons importants (logs, usage réservé) (`channels`) :

| Clé | Contenu |
|---|---|
| `voice_channel` | LOGS Connexions, déconnexions et changements de salon vocal |
| `message_delete_channel` | LOGS Messages supprimés (un par un ou en masse) |
| `message_edit_channel` | LOGS Messages modifiés (ancien et nouveau contenu) |
| `lock_channel` | LOGS Verrouillages et déverrouillages de salon (`/lock`) |
| `channel_log_channel` | LOGS Salons créés, modifiés, supprimés, et permissions d'un salon (pour un rôle ou un membre) |
| `role_log_channel` | LOGS Rôles créés, modifiés (dont permissions), supprimés |
| `member_log_channel` | LOGS Rôles et pseudo des membres, départs |
| `moderation_channel` | LOGS Bannissements, expulsions, exclusions temporaires (et leur levée) |
| `honeypot_channel` | Tout message écrit ici provoque le bannissement de son auteur |

Les logs de salons, rôles, membres et sanctions viennent du journal d'audit Discord : ils indiquent l'auteur de l'action et la raison éventuelle. Le bot a besoin de la permission **Voir les logs du serveur**. Les actions faites par le bot lui-même (ex. `/lock`) n'y sont pas journalisées, elles ont leurs propres logs.

Pour copier un ID : activer le mode développeur (Paramètres → Avancés), puis clic droit → « Copier l'identifiant ».

Le bot a besoin de l'intent privilégié **Message Content** (Discord Developer Portal → Bot), sinon il ne peut pas se connecter.

## Annonces des enseignants

`/annonce` (rôle `teacherRole` ou admin) ouvre un formulaire : les classes (une ou plusieurs, parmi `announcementChannels`) et le message (3500 caractères, markdown accepté). Le bot montre ensuite un aperçu privé avec **Envoyer** et **Annuler**. À l'envoi, l'annonce part dans le salon de chaque classe, signée du nom et de l'avatar de l'enseignant, avec les rôles de la classe (`groups`) mentionnés.

- Seuls les rôles de la classe sont notifiés : un `@everyone` ou un autre rôle écrit dans le message ne prévient personne.
- Le bot doit pouvoir écrire dans les salons d'annonces, et mentionner les rôles des classes (rôles « mentionnables », ou permission **Mentionner @everyone, @here et tous les rôles**).
- Les aperçus en attente sont en mémoire : après un redémarrage du bot, il faut relancer `/annonce`.

## Statut des services

`/status` (ouvert à tous, réponse privée) dit si les services de `statusTargets` répondent, et depuis quand. Le bot les vérifie lui-même toutes les `statusIntervalSeconds` secondes (60 par défaut, 30 au minimum), depuis son serveur : si Moodle est vert ici mais ne charge pas chez soi, le problème vient de sa connexion. `/status` affiche le dernier résultat, sans relancer de vérification.

- **En ligne** : réponse HTTP inférieure à 500 en moins de 5 secondes (une redirection vers la page de connexion compte).
- **En panne** : réponse 5xx (page de maintenance comprise).
- **Injoignable** : pas de réponse (délai dépassé, DNS, connexion refusée).

Un échec est retenté jusqu'à 3 fois, à 5 secondes d'intervalle : un service n'est hors ligne que si les 3 essais échouent. Le « depuis » ne change qu'au passage entre en ligne et hors ligne. L'historique est en mémoire : après un redémarrage, tant que l'état n'a pas changé, le bot affiche « depuis au moins » l'heure de son démarrage.

## StudySuite

`/study` regroupe ce qui vient de [StudySuite](https://study-info.umontp.fr) (le planning de l'IUT).

| Commande | Description |
|---|---|
| `/study planning [periode] [date] [groupe] [prive]` | Emploi du temps d'un jour (par défaut) ou de la semaine. `date` accepte « demain », « lundi », « vendredi prochain », « 15/09 », « 2026-09-15 » ; sans date, aujourd'hui, ou lundi le week-end. `prive` n'affiche la réponse qu'à soi. |
| `/study devoirs [passes]` | Tes devoirs à rendre (d'après ton compte StudySuite), chacun avec un bouton « Fait » au bout de la ligne (12 au plus, le reste sur le site). Toujours en privé. `passes` ajoute ceux des deux dernières semaines. |
| `/study devoir-ajouter titre date [heure] [matiere] [description] [groupe]` | Ajoute un devoir sur StudySuite à ton nom et l'annonce dans le salon, avec un bouton « Fait » que chacun peut cliquer pour le cocher dans sa propre liste. `heure` accepte « 18h », « 8h30 », « midi » ; par défaut 23h59 (heure de Paris). |

**Les devoirs se font au nom du membre.** Le bot appelle StudySuite avec sa clé et l'en-tête `X-Acting-Discord-User` : la requête compte comme celle du membre (son groupe, ses cases cochées, son nom sur les devoirs qu'il ajoute). Il faut donc un compte StudySuite lié à son Discord : sans, le bot l'invite à se connecter une fois sur le site avec Discord. StudySuite vérifie aussi que le compte est validé et qu'il a accès au groupe.

**Le groupe.** Sans l'option `groupe`, c'est la classe du membre, lue depuis ses rôles : les associations rôle Discord → groupe se gèrent dans l'admin de StudySuite (celles qui valident les comptes à la connexion), et le bot les lit avec la clé `studySuite.apiKey`. Sans clé, l'option `groupe` est obligatoire. Si un membre a plusieurs rôles de classe (sa promo et son TP), c'est le plus précis qui compte.

**L'héritage.** Le planning d'un groupe contient aussi les cours de ses groupes parents : un CM de promo est rattaché à la promo, pas à chaque TP. Ces cours affichent le groupe dont ils viennent (« BUT1 », « S1 »…). Les groupes cachés du site (les semestres) sont remplacés par leur parent visible.

**Les heures.** Elles s'affichent en timestamps Discord (`<t:…:t>`) : chacun les voit dans son fuseau et au format de sa langue. L'API renvoie l'heure de Paris étiquetée UTC (`08:00:00.000Z` = 8h à Paris) : le bot lit donc cette heure comme une heure de Paris pour obtenir l'instant réel (06:00 UTC en été), et non comme de l'UTC, ce qui décalerait tout d'une ou deux heures.

## Docker

```sh
docker compose pull
docker compose up -d
```

- `./config.json` est monté en lecture sur `/app/config.json`.
- `./data` est monté sur `/app/data`, qui est aussi le dossier courant du bot : avec `"databasePath": "data.db"`, la base est donc `./data/data.db` sur l'hôte et survit à la recréation du conteneur.
- Il faut monter le **dossier** et pas seulement le fichier `data.db` : SQLite crée et supprime des fichiers temporaires (`data.db-journal`) à côté de la base, ce qui est impossible sur un fichier monté seul. De plus, si le fichier n'existe pas sur l'hôte, Docker crée un dossier à sa place.
- Garder un `databasePath` relatif. Un chemin absolu hors de `/app/data` placerait la base hors du volume.

## Documentation Libs

[JDA](https://docs.jda.wiki/net/dv8tion/jda/api/hooks/ListenerAdapter.html#onMessageDelete(net.dv8tion.jda.api.events.message.MessageDeleteEvent))
