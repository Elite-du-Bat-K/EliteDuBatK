package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.config.ConfigStructure;
import fr.umontpellier.iut.discordbot.database.dataobjects.CachedMessageData;
import fr.umontpellier.iut.discordbot.database.dataobjects.GdprDeletionRequest;
import fr.umontpellier.iut.discordbot.database.dataobjects.PreservedMessage;
import fr.umontpellier.iut.discordbot.database.repositories.CachedMessageRepository;
import fr.umontpellier.iut.discordbot.database.repositories.GdprDeletionRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollCommentRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollVoterRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PreservedMessageRepository;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GdprService {
	public static final long VERIFICATION_WINDOW_MILLIS = 48L * 60L * 60L * 1000L;
	private static final long CHECK_INTERVAL_MINUTES = 15L;
	private static final int ALERT_COLOR = 0xFFA500;
	private static final int COMPLETED_COLOR = 0x2ECC71;

	private final Logger logger = LoggerFactory.getLogger(GdprService.class);
	private final Bot bot;
	private final GdprDeletionRepository deletionRepository;
	private final PreservedMessageRepository preservedRepository;
	private final CachedMessageRepository cachedMessageRepository;
	private final PollVoterRepository pollVoterRepository;
	private final PollCommentRepository pollCommentRepository;
	private final ScheduledExecutorService scheduler;

	public record BatchPreserveResult(List<Long> preserved, List<Long> alreadyPreserved, List<Long> notFound) {
	}

	public record BatchUnpreserveResult(List<Long> removed, List<Long> notFound) {
	}

	public GdprService(Bot bot) {
		this.bot = bot;
		this.deletionRepository = bot.getRepositories().getRepository(GdprDeletionRepository.class);
		this.preservedRepository = bot.getRepositories().getRepository(PreservedMessageRepository.class);
		this.cachedMessageRepository = bot.getRepositories().getCachedMessageRepository();
		this.pollVoterRepository = bot.getRepositories().getRepository(PollVoterRepository.class);
		this.pollCommentRepository = bot.getRepositories().getRepository(PollCommentRepository.class);
		this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "GdprDeletionScheduler");
			thread.setDaemon(true);
			return thread;
		});
	}

	public void start() {
		this.scheduler.scheduleWithFixedDelay(this::processDueRequests, 1L, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
		Runtime.getRuntime().addShutdownHook(new Thread(this.scheduler::shutdown));
	}

	public Optional<GdprDeletionRequest> createDeletionRequest(String userId) {
		Optional<GdprDeletionRequest> created = Optional.empty();
		Optional<GdprDeletionRequest> existing = this.deletionRepository.findPendingByUserId(userId);
		boolean canCreate = existing.isEmpty();

		if (canCreate) {
			long now = System.currentTimeMillis();
			long executeAt = now + VERIFICATION_WINDOW_MILLIS;
			GdprDeletionRequest request = new GdprDeletionRequest(
					0L, userId, now, executeAt, GdprDeletionRequest.STATUS_PENDING, null, null);
			this.deletionRepository.insert(request);
			created = this.deletionRepository.findPendingByUserId(userId);

			if (created.isPresent()) {
				GdprDeletionRequest saved = created.get();
				String details = String.format(
						"Demande `#%d` soumise par <@%s> (`%s`).\nExécution automatique prévue le <t:%d:F>.\n"
								+ "Utilisez `/rgpd-admin review %d` pour inspecter ses messages et `/rgpd-admin preserve` pour isoler d'éventuelles preuves.",
						saved.getId(), userId, userId, executeAt / 1000L, saved.getId());
				this.bot.getLogSender().sendLog(
						ConfigStructure.SystemChannel.MODERATION_CHANNEL,
						"# Demande d'effacement RGPD",
						ALERT_COLOR,
						details);
			}
		}

		return created;
	}

	public Optional<GdprDeletionRequest> getPendingRequest(String userId) {
		return this.deletionRepository.findPendingByUserId(userId);
	}

	public boolean cancelPendingRequest(String userId) {
		boolean cancelled = false;
		Optional<GdprDeletionRequest> pendingOpt = this.deletionRepository.findPendingByUserId(userId);

		if (pendingOpt.isPresent()) {
			GdprDeletionRequest current = pendingOpt.get();
			GdprDeletionRequest updated = new GdprDeletionRequest(
					current.getId(), current.getUserId(), current.getRequestedAt(), current.getExecuteAt(),
					GdprDeletionRequest.STATUS_CANCELLED, userId, null);
			cancelled = this.deletionRepository.update(updated) > 0;
		}

		return cancelled;
	}

	public List<GdprDeletionRequest> getAllPendingRequests() {
		return this.deletionRepository.findAllPending();
	}

	public Optional<GdprDeletionRequest> findRequestById(long requestId) {
		return this.deletionRepository.findByPrimaryKey(requestId);
	}

	public List<CachedMessageData> getUserCachedMessages(long userId) {
		return this.cachedMessageRepository.findByAuthorId(userId);
	}

	public List<PreservedMessage> getUserPreservedMessages(long userId) {
		return this.preservedRepository.findByAuthorId(userId);
	}

	public BatchPreserveResult preserveMessages(List<Long> messageIds, String adminId, String reason) {
		List<Long> preserved = new ArrayList<>();
		List<Long> alreadyPreserved = new ArrayList<>();
		List<Long> notFound = new ArrayList<>();
		long now = System.currentTimeMillis();

		for (Long messageId : messageIds) {
			boolean existsPreserved = this.preservedRepository.findByPrimaryKey(messageId).isPresent();
			if (existsPreserved) {
				alreadyPreserved.add(messageId);
			}

			if (!existsPreserved) {
				Optional<CachedMessageData> dataOpt = this.resolveCachedMessage(messageId);
				if (dataOpt.isPresent()) {
					CachedMessageData data = dataOpt.get();
					PreservedMessage entry = new PreservedMessage(
							data.getMessageId(), data.getChannelId(), data.getAuthorId(),
							data.getContent(), data.getTimestamp(), adminId, now, reason);
					boolean inserted = this.preservedRepository.insertIfAbsent(entry);
					if (inserted) {
						preserved.add(messageId);
					}
					if (!inserted) {
						alreadyPreserved.add(messageId);
					}
				}
				if (dataOpt.isEmpty()) {
					notFound.add(messageId);
				}
			}
		}

		return new BatchPreserveResult(preserved, alreadyPreserved, notFound);
	}

	public BatchUnpreserveResult unpreserveMessages(List<Long> messageIds) {
		List<Long> removed = new ArrayList<>();
		List<Long> notFound = new ArrayList<>();

		for (Long messageId : messageIds) {
			int deleted = this.preservedRepository.deleteByPrimaryKey(messageId);
			if (deleted > 0) {
				removed.add(messageId);
			}
			if (deleted == 0) {
				notFound.add(messageId);
			}
		}

		return new BatchUnpreserveResult(removed, notFound);
	}

	public boolean approveRequest(long requestId, String adminId, String refusalReason) {
		boolean approved = false;
		Optional<GdprDeletionRequest> requestOpt = this.deletionRepository.findByPrimaryKey(requestId);
		boolean isPending = requestOpt.isPresent()
				&& GdprDeletionRequest.STATUS_PENDING.equals(requestOpt.get().getStatus());

		if (isPending) {
			approved = this.executeDeletion(requestOpt.get(), adminId, refusalReason);
		}

		return approved;
	}

	public void processDueRequests() {
		try {
			long now = System.currentTimeMillis();
			List<GdprDeletionRequest> dueRequests = this.deletionRepository.findDuePending(now);
			for (GdprDeletionRequest request : dueRequests) {
				this.executeDeletion(request, "SYSTEM", null);
			}
		} catch (Exception e) {
			this.logger.error("Error processing due GDPR deletion requests", e);
		}
	}

	private Optional<CachedMessageData> resolveCachedMessage(long messageId) {
		Optional<CachedMessageData> resolved = this.cachedMessageRepository.findByPrimaryKey(messageId);

		if (resolved.isEmpty()) {
			CachedMessage inMemory = this.bot.getMessageCacheService().getMessage(messageId);
			if (inMemory != null) {
				resolved = Optional.of(new CachedMessageData(
						inMemory.getMessageId(), inMemory.getChannelId(), inMemory.getAuthorId(),
						inMemory.getContent(), inMemory.getTimestamp()));
			}
		}

		return resolved;
	}

	private boolean executeDeletion(GdprDeletionRequest request, String reviewerId, String refusalReason) {
		boolean executed = false;
		String userId = request.getUserId();

		try {
			long numericUserId = Long.parseLong(userId);
			int deletedMessages = this.cachedMessageRepository.deleteByAuthorId(numericUserId);
			this.bot.getMessageCacheService().removeMessagesByAuthor(numericUserId);
			int deletedVotes = this.pollVoterRepository.deleteByUserId(userId);
			int deletedComments = this.pollCommentRepository.deleteByUserId(userId);
			int preservedCount = this.preservedRepository.findByAuthorId(numericUserId).size();

			GdprDeletionRequest completed = new GdprDeletionRequest(
					request.getId(), userId, request.getRequestedAt(), request.getExecuteAt(),
					GdprDeletionRequest.STATUS_COMPLETED, reviewerId, refusalReason);
			this.deletionRepository.update(completed);
			executed = true;

			this.notifyUserCompletion(userId, deletedMessages, deletedVotes, deletedComments, preservedCount,
					refusalReason);

			String logDetails = String.format(
					"Demande `#%d` exécutée pour <@%s> (`%s`) par `%s`.\n"
							+ "Messages supprimés : %d | Émargements sondages : %d | Commentaires : %d | Preuves conservées : %d",
					request.getId(), userId, userId, reviewerId,
					deletedMessages, deletedVotes, deletedComments, preservedCount);
			this.bot.getLogSender().sendLog(
					ConfigStructure.SystemChannel.MODERATION_CHANNEL,
					"# Effacement RGPD exécuté",
					COMPLETED_COLOR,
					logDetails);
		} catch (Exception e) {
			this.logger.error("Failed to execute GDPR deletion for request {}", request.getId(), e);
		}

		return executed;
	}

	private void notifyUserCompletion(String userId, int deletedMessages, int deletedVotes, int deletedComments,
			int preservedCount, String refusalReason) {
		StringBuilder message = new StringBuilder();
		message.append("Ta demande d'effacement de données personnelles (RGPD) a été traitée.\n");
		message.append("- Messages supprimés du cache : ").append(deletedMessages).append("\n");
		message.append("- Participations aux sondages supprimées : ").append(deletedVotes).append("\n");
		message.append("- Commentaires de sondages supprimés : ").append(deletedComments);

		boolean hasPreserved = preservedCount > 0;
		if (hasPreserved) {
			message.append("\n\n⚠️ **Rétention partielle** : ").append(preservedCount)
					.append(" message(s) ont été conservés par l'administration au titre d'un incident de modération (Art. 17.3 RGPD).");
			boolean hasReason = refusalReason != null && !refusalReason.isBlank();
			if (hasReason) {
				message.append("\nMotif : ").append(refusalReason);
			}
		}

		String finalContent = message.toString();
		this.bot.getJda().retrieveUserById(userId).queue(
				user -> user.openPrivateChannel().queue(
						channel -> channel.sendMessage(finalContent).queue(success -> {
						}, error -> {
						}),
						error -> {
						}),
				error -> {
				});
	}
}