package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.commands.annuaire.DirectoryFormatter;
import fr.umontpellier.iut.discordbot.config.ConfigStructure;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryCategory;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryPendingChange;
import fr.umontpellier.iut.discordbot.database.repositories.DirectoryPendingChangeRepository;
import fr.umontpellier.iut.discordbot.database.repositories.DirectoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class DirectoryService {
	private static final int PENDING_COLOR = 0xF1C40F;

	private final Logger logger = LoggerFactory.getLogger(DirectoryService.class);
	private final Bot bot;
	private final DirectoryRepository directoryRepository;
	private final DirectoryPendingChangeRepository pendingRepository;

	public record PageResult(List<DirectoryEntry> entries, int page, int totalPages, int totalEntries) {
	}

	public record MutationResult(boolean success, boolean pending, long referenceId, String message) {
	}

	public DirectoryService(Bot bot) {
		this.bot = bot;
		this.directoryRepository = bot.getRepositories().getRepository(DirectoryRepository.class);
		this.pendingRepository = bot.getRepositories().getRepository(DirectoryPendingChangeRepository.class);
	}

	public List<DirectoryEntry> search(String query) {
		return this.directoryRepository.searchDirectory(query);
	}

	public PageResult getPage(int requestedPage, int pageSize) {
		int totalEntries = this.directoryRepository.findAll().size();
		int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / pageSize));
		int page = Math.min(Math.max(1, requestedPage), totalPages);
		List<DirectoryEntry> entries = this.directoryRepository.getPaginated(page, pageSize);
		return new PageResult(entries, page, totalPages, totalEntries);
	}

	public Optional<DirectoryEntry> findEntryById(long id) {
		return this.directoryRepository.findByPrimaryKey(id);
	}

	public List<DirectoryPendingChange> getPendingChanges() {
		return this.pendingRepository.findAllOrdered();
	}

	public MutationResult requestAdd(DirectoryCategory category, String rawLastName, String rawFirstName,
			String rawEmail, String rawDescription, Long discordUserId, String requesterId, boolean isAdmin) {
		String email = DirectoryFormatter.normalizeEmail(rawEmail);
		boolean isEmailValid = DirectoryFormatter.isValidEmail(email);
		MutationResult result = new MutationResult(false, false, 0L,
				"Adresse e-mail invalide. Vérifiez le format (ex: prenom.nom@umontpellier.fr).");

		if (isEmailValid) {
			String lastName = DirectoryFormatter.formatLastName(rawLastName, category);
			String firstName = DirectoryFormatter.formatFirstName(rawFirstName);
			String description = this.cleanDescription(rawDescription);

			if (isAdmin) {
				result = this.applyAdd(category.name(), lastName, firstName, email, description, discordUserId);
			}

			if (!isAdmin) {
				DirectoryPendingChange pending = new DirectoryPendingChange(
						0L, DirectoryPendingChange.OP_ADD, null, lastName, firstName, email,
						category.name(), description, discordUserId, requesterId, System.currentTimeMillis());
				result = this.queuePendingChange(pending);
			}
		}

		return result;
	}

	public MutationResult requestEdit(long entryId, String rawLastName, String rawFirstName, String rawEmail,
			String rawDescription, String requesterId, boolean isAdmin) {
		String email = DirectoryFormatter.normalizeEmail(rawEmail);
		boolean isEmailValid = DirectoryFormatter.isValidEmail(email);
		MutationResult result = new MutationResult(false, false, 0L,
				"Adresse e-mail invalide. Vérifiez le format (ex: prenom.nom@umontpellier.fr).");

		if (isEmailValid) {
			Optional<DirectoryEntry> entryOpt = this.directoryRepository.findByPrimaryKey(entryId);
			result = new MutationResult(false, false, 0L, "Échec de la modification. Entrée introuvable.");

			if (entryOpt.isPresent()) {
				DirectoryEntry existing = entryOpt.get();
				DirectoryCategory category = DirectoryCategory.fromString(existing.getCategory());
				String lastName = DirectoryFormatter.formatLastName(rawLastName, category);
				String firstName = DirectoryFormatter.formatFirstName(rawFirstName);
				String description = this.cleanDescription(rawDescription);

				if (isAdmin) {
					result = this.applyEdit(existing, lastName, firstName, email, description);
				}

				if (!isAdmin) {
					DirectoryPendingChange pending = new DirectoryPendingChange(
							0L, DirectoryPendingChange.OP_EDIT, entryId, lastName, firstName, email,
							existing.getCategory(), description, existing.getDiscordUserId(),
							requesterId, System.currentTimeMillis());
					result = this.queuePendingChange(pending);
				}
			}
		}

		return result;
	}

	public MutationResult requestDelete(long entryId, String requesterId, boolean isAdmin) {
		MutationResult result = new MutationResult(false, false, 0L,
				"Échec de la suppression. Entrée introuvable ou ID invalide.");
		Optional<DirectoryEntry> entryOpt = this.directoryRepository.findByPrimaryKey(entryId);

		if (entryOpt.isPresent()) {
			DirectoryEntry existing = entryOpt.get();

			if (isAdmin) {
				result = this.applyDelete(entryId);
			}

			if (!isAdmin) {
				DirectoryPendingChange pending = new DirectoryPendingChange(
						0L, DirectoryPendingChange.OP_DELETE, entryId, existing.getLastName(),
						existing.getFirstName(), existing.getEmail(), existing.getCategory(),
						existing.getDescription(), existing.getDiscordUserId(),
						requesterId, System.currentTimeMillis());
				result = this.queuePendingChange(pending);
			}
		}

		return result;
	}

	public MutationResult approveChange(long changeId, String adminId) {
		MutationResult result = new MutationResult(false, false, 0L, "Demande introuvable.");
		Optional<DirectoryPendingChange> changeOpt = this.pendingRepository.findByPrimaryKey(changeId);

		if (changeOpt.isPresent()) {
			DirectoryPendingChange change = changeOpt.get();
			boolean isAdd = DirectoryPendingChange.OP_ADD.equals(change.getOperation());
			boolean isEdit = DirectoryPendingChange.OP_EDIT.equals(change.getOperation());
			boolean isDelete = DirectoryPendingChange.OP_DELETE.equals(change.getOperation());

			if (isAdd) {
				result = this.applyAdd(change.getCategory(), change.getLastName(), change.getFirstName(),
						change.getEmail(), change.getDescription(), change.getDiscordUserId());
			}

			if (isEdit && change.getTargetEntryId() != null) {
				Optional<DirectoryEntry> targetOpt = this.directoryRepository
						.findByPrimaryKey(change.getTargetEntryId());
				result = new MutationResult(false, false, 0L, "L'entrée cible n'existe plus.");
				if (targetOpt.isPresent()) {
					result = this.applyEdit(targetOpt.get(), change.getLastName(), change.getFirstName(),
							change.getEmail(), change.getDescription());
				}
			}

			if (isDelete && change.getTargetEntryId() != null) {
				result = this.applyDelete(change.getTargetEntryId());
			}

			if (result.success()) {
				this.pendingRepository.deleteByPrimaryKey(changeId);
				this.logger.info("Directory change {} approved by {}", changeId, adminId);
				this.notifyRequester(change.getRequesterId(),
						"Ta demande de modification d'annuaire (`#" + changeId + "`, " + change.getOperation()
								+ ") a été validée par l'administration.");
			}
		}

		return result;
	}

	public MutationResult rejectChange(long changeId, String adminId, String reason) {
		MutationResult result = new MutationResult(false, false, 0L, "Demande introuvable.");
		Optional<DirectoryPendingChange> changeOpt = this.pendingRepository.findByPrimaryKey(changeId);

		if (changeOpt.isPresent()) {
			DirectoryPendingChange change = changeOpt.get();
			int deleted = this.pendingRepository.deleteByPrimaryKey(changeId);
			if (deleted > 0) {
				this.logger.info("Directory change {} rejected by {}", changeId, adminId);
				String dm = "Ta demande de modification d'annuaire (`#" + changeId + "`, " + change.getOperation()
						+ ") a été refusée par l'administration.";
				boolean hasReason = reason != null && !reason.isBlank();
				if (hasReason) {
					dm = dm + "\nMotif : " + reason;
				}
				this.notifyRequester(change.getRequesterId(), dm);
				result = new MutationResult(true, false, changeId, "Demande `#" + changeId + "` refusée et supprimée.");
			}
		}

		return result;
	}

	private MutationResult applyAdd(String category, String lastName, String firstName, String email,
			String description, Long discordUserId) {
		MutationResult result = new MutationResult(false, false, 0L, "Échec de l'ajout de l'entrée.");
		DirectoryEntry entry = new DirectoryEntry();
		entry.setLastName(lastName);
		entry.setFirstName(firstName);
		entry.setEmail(email);
		entry.setCategory(category);
		entry.setDescription(description);
		entry.setDiscordUserId(discordUserId);

		try {
			this.directoryRepository.insert(entry);
			result = new MutationResult(true, false, 0L, "Entrée ajoutée avec succès à l'annuaire.");
		} catch (Exception e) {
			this.logger.error("Failed to insert directory entry", e);
		}

		return result;
	}

	private MutationResult applyEdit(DirectoryEntry entry, String lastName, String firstName, String email,
			String description) {
		MutationResult result = new MutationResult(false, false, 0L, "Échec de la modification de l'entrée.");
		entry.setLastName(lastName);
		entry.setFirstName(firstName);
		entry.setEmail(email);
		entry.setDescription(description);

		try {
			this.directoryRepository.update(entry);
			result = new MutationResult(true, false, entry.getId(), "Entrée modifiée avec succès.");
		} catch (Exception e) {
			this.logger.error("Failed to update directory entry {}", entry.getId(), e);
		}

		return result;
	}

	private MutationResult applyDelete(long entryId) {
		MutationResult result = new MutationResult(false, false, 0L,
				"Échec de la suppression. Entrée introuvable ou ID invalide.");
		int deleted = this.directoryRepository.deleteByPrimaryKey(entryId);
		if (deleted > 0) {
			result = new MutationResult(true, false, entryId, "Entrée supprimée avec succès.");
		}
		return result;
	}

	private MutationResult queuePendingChange(DirectoryPendingChange pending) {
		MutationResult result = new MutationResult(false, false, 0L, "Échec de l'enregistrement de la demande.");

		try {
			this.pendingRepository.insert(pending);
			Optional<DirectoryPendingChange> savedOpt = this.pendingRepository
					.findLatestByRequester(pending.getRequesterId());
			long changeId = savedOpt.map(DirectoryPendingChange::getId).orElse(0L);
			this.notifyAdmins(changeId, pending);
			result = new MutationResult(true, true, changeId,
					"Ta demande (`#" + changeId + "`) a été soumise à l'équipe d'administration pour validation.");
		} catch (Exception e) {
			this.logger.error("Failed to queue directory pending change", e);
		}

		return result;
	}

	private void notifyAdmins(long changeId, DirectoryPendingChange change) {
		StringBuilder details = new StringBuilder();
		details.append("Demande `#").append(changeId).append("` (**").append(change.getOperation())
				.append("**) par <@").append(change.getRequesterId()).append(">\n");

		if (change.getTargetEntryId() != null) {
			details.append("Entrée cible : `#").append(change.getTargetEntryId()).append("`\n");
		}

		details.append("Nom : ").append(change.getLastName());
		if (change.getFirstName() != null && !change.getFirstName().isEmpty()) {
			details.append(" ").append(change.getFirstName());
		}
		details.append("\nEmail : `").append(change.getEmail()).append("`");
		details.append("\nCatégorie : ").append(change.getCategory());

		if (change.getDescription() != null && !change.getDescription().isEmpty()) {
			details.append("\nDescription : ").append(change.getDescription());
		}

		details.append("\n\nValider : `/annuaire-admin approve ").append(changeId)
				.append("` | Refuser : `/annuaire-admin reject ").append(changeId).append("`");

		this.bot.getLogSender().sendLog(
				ConfigStructure.SystemChannel.CONTACT_CHANNEL,
				"# Demande de modification d'annuaire",
				PENDING_COLOR,
				details.toString());
	}

	private void notifyRequester(String userId, String content) {
		this.bot.getJda().retrieveUserById(userId).queue(
				user -> user.openPrivateChannel().queue(
						channel -> channel.sendMessage(content).queue(success -> {
						}, error -> {
						}),
						error -> {
						}),
				error -> {
				});
	}

	private String cleanDescription(String rawDescription) {
		String cleaned = null;
		boolean hasValue = rawDescription != null && !rawDescription.trim().isEmpty();

		if (hasValue) {
			cleaned = rawDescription.trim();
		}

		return cleaned;
	}
}