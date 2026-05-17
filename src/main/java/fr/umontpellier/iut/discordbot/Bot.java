package fr.umontpellier.iut.discordbot;

import fr.umontpellier.iut.discordbot.commands.CommandManager;
import fr.umontpellier.iut.discordbot.config.ConfigLoader;
import fr.umontpellier.iut.discordbot.database.RepositoryFactory;
import fr.umontpellier.iut.discordbot.events.EventManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Collections;

public class Bot implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Bot.class);

	@NotNull
	private final ConfigLoader config;
	@NotNull
	private final RepositoryFactory repositories;
	@NotNull
	private final CommandManager commands;
	@NotNull
	private final EventManager events;
	private JDA jda;

	public Bot() throws SQLException {
		config = new ConfigLoader();
		repositories = new RepositoryFactory(this);
		commands = new CommandManager(this);
		events = new EventManager(this);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				repositories.close();
			} catch (SQLException e) {
				logger.error("Failed to close database connection", e);
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

	@Override
	public void run() {
		this.jda = JDABuilder.createLight(config.get().getToken(), Collections.emptyList())
				.build();

		events.registerEvents();
	}
}
