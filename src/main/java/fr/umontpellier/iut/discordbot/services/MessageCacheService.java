package fr.umontpellier.iut.discordbot.services;

import fr.umontpellier.iut.discordbot.database.dataobjects.CachedMessageData;
import fr.umontpellier.iut.discordbot.database.repositories.CachedMessageRepository;
import fr.umontpellier.iut.discordbot.lib.BoundedCache;
import fr.umontpellier.iut.discordbot.lib.CachedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageCacheService {

	private static final int BATCH_SIZE = 50;
	private static final long NINETY_DAYS_MILLIS = 90L * 24L * 60L * 60L * 1000L;

	private final BoundedCache<Long, CachedMessage> memoryCache;
	private final BlockingQueue<CachedMessage> pendingInserts;
	private final CachedMessageRepository repository;
	private final AtomicBoolean isRunning;
	private final Thread daemonThread;
	private final ScheduledExecutorService scheduler;

	public MessageCacheService(CachedMessageRepository repository, int maxCacheSize) {
		this.repository = repository;
		this.memoryCache = new BoundedCache<>(maxCacheSize);
		this.pendingInserts = new LinkedBlockingQueue<>();
		this.isRunning = new AtomicBoolean(true);
		this.scheduler = Executors.newSingleThreadScheduledExecutor();

		this.daemonThread = new Thread(this::runDaemon);
		this.daemonThread.setDaemon(true);
		this.daemonThread.setName("MessageCacheBatchWriter");
	}

	public boolean initialize() {
		boolean success = false;
		boolean tableCreated = this.repository.createTableIfNotExists();

		if (tableCreated) {
			this.loadRecentMessagesFromDatabase();
			this.daemonThread.start();
			this.scheduler.scheduleAtFixedRate(this::purgeOldMessages, 1, 1, TimeUnit.DAYS);
			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
			success = true;
		}

		return success;
	}

	public boolean cacheMessage(CachedMessage message) {
		boolean success = false;
		boolean isValid = message != null;

		if (isValid) {
			this.memoryCache.put(message.getMessageId(), message);
			this.pendingInserts.offer(message);
			success = true;
		}

		return success;
	}

	public CachedMessage getMessage(long messageId) {
		return this.memoryCache.get(messageId);
	}

	private void runDaemon() {
		while (this.isRunning.get()) {
			this.processBatch();
		}
	}

	private boolean processBatch() {
		boolean success = false;
		List<CachedMessage> batch = new ArrayList<>();
		boolean canPoll = true;

		try {
			CachedMessage first = this.pendingInserts.poll(5, TimeUnit.SECONDS);
			boolean hasFirst = first != null;
			if (hasFirst) {
				batch.add(first);
				this.pendingInserts.drainTo(batch, BATCH_SIZE - 1);
			}
		} catch (InterruptedException e) {
			this.isRunning.set(false);
			Thread.currentThread().interrupt();
			canPoll = false;
		}

		boolean hasMessagesToInsert = canPoll && !batch.isEmpty();

		if (hasMessagesToInsert) {
			List<CachedMessageData> dataBatch = new ArrayList<>();
			for (CachedMessage msg : batch) {
				dataBatch.add(new CachedMessageData(
						msg.getMessageId(),
						msg.getChannelId(),
						msg.getAuthorId(),
						msg.getContent(),
						msg.getTimestamp()));
			}
			success = this.repository.insertBatch(dataBatch);
		}

		return success;
	}

	private boolean loadRecentMessagesFromDatabase() {
		boolean success = false;
		List<CachedMessageData> recentData = this.repository.fetchRecent(1000);
		boolean hasData = recentData != null;

		if (hasData) {
			for (CachedMessageData data : recentData) {
				this.memoryCache.put(data.getMessageId(), new CachedMessage(
						data.getMessageId(),
						data.getChannelId(),
						data.getAuthorId(),
						data.getContent(),
						data.getTimestamp()));
			}
			success = true;
		}

		return success;
	}

	private boolean purgeOldMessages() {
		long threshold = System.currentTimeMillis() - NINETY_DAYS_MILLIS;
		return this.repository.deleteOlderThan(threshold);
	}

	private void shutdown() {
		this.isRunning.set(false);
		this.scheduler.shutdown();

		boolean threadWasAlive = this.daemonThread.isAlive();
		if (threadWasAlive) {
			try {
				this.daemonThread.join(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		boolean hasRemaining = !this.pendingInserts.isEmpty();
		if (hasRemaining) {
			List<CachedMessage> remaining = new ArrayList<>();
			this.pendingInserts.drainTo(remaining);

			List<CachedMessageData> dataBatch = new ArrayList<>();
			for (CachedMessage msg : remaining) {
				dataBatch.add(new CachedMessageData(
						msg.getMessageId(),
						msg.getChannelId(),
						msg.getAuthorId(),
						msg.getContent(),
						msg.getTimestamp()));
			}
			this.repository.insertBatch(dataBatch);
		}
	}
}