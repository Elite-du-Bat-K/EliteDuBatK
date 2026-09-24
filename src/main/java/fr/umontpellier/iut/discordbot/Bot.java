package fr.umontpellier.iut.discordbot;

import fr.umontpellier.iut.discordbot.commands.CommandManager;
import fr.umontpellier.iut.discordbot.config.ConfigLoader;
import fr.umontpellier.iut.discordbot.database.RepositoryFactory;
import fr.umontpellier.iut.discordbot.events.EventManager;
import fr.umontpellier.iut.discordbot.services.LogSender;
import fr.umontpellier.iut.discordbot.services.MessageCacheService;
import fr.umontpellier.iut.discordbot.studysuite.StudySuiteClient;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class Bot implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);
	private static final int MAX_CACHED_MESSAGES = 10000;

	@NotNull
	private final ConfigLoader config;
	@NotNull
	private final RepositoryFactory repositories;
	@NotNull
	private final CommandManager commands;
	@NotNull
	private final EventManager events;
	private JDA jda;

	private final LogSender logSender;
	@NotNull
	private final StudySuiteClient studySuite;

	@NotNull
	private final MessageCacheService messageCacheService;

	public Bot() throws SQLException {
		config = new ConfigLoader();
		repositories = new RepositoryFactory(this);
		commands = new CommandManager(this);
		events = new EventManager(this);
		logSender = new LogSender(this);
		studySuite = new StudySuiteClient(config.get().getStudySuite());

		messageCacheService = new MessageCacheService(repositories.getCachedMessageRepository(), MAX_CACHED_MESSAGES);
		messageCacheService.initialize();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				repositories.close();
			} catch (SQLException e) {
				LOGGER.error("Failed to close database connection", e);
			}
		}));
	}

	@NotNull
	public ConfigLoader getConfig() {
		return config;
	}

	@NotNull
	public CommandManager getCommandManager() {
		return commands;
	}

	@NotNull
	public RepositoryFactory getRepositories() {
		return repositories;
	}

	@NotNull
	public JDA getJda() {
		if (jda == null) {
			throw new IllegalStateException("JDA is not initialized yet. Please run the bot first.");
		}
		return jda;
	}

	@NotNull
	public MessageCacheService getMessageCacheService() {
		return messageCacheService;
	}

	public LogSender getLogSender() {
		return logSender;
	}

	@NotNull
	public StudySuiteClient getStudySuite() {
		return studySuite;
	}

	@Override
	public void run() {
		JDABuilder builder = JDABuilder.createLight(config.get().getToken(), List.of(
				GatewayIntent.GUILD_VOICE_STATES,
				GatewayIntent.GUILD_MESSAGES,
				GatewayIntent.MESSAGE_CONTENT,
				GatewayIntent.GUILD_MEMBERS,
				GatewayIntent.GUILD_MODERATION)).enableCache(CacheFlag.VOICE_STATE)
				.setBulkDeleteSplittingEnabled(false);

		events.registerEvents(builder);
		this.jda = builder.build();
	}
}