package fr.umontpellier.iut.discordbot;

import fr.umontpellier.iut.discordbot.commands.CommandManager;
import fr.umontpellier.iut.discordbot.config.ConfigLoader;
import fr.umontpellier.iut.discordbot.database.RepositoryFactory;
import fr.umontpellier.iut.discordbot.events.EventManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collections;

public class Bot implements Runnable {
	@NotNull
	private final ConfigLoader config;
	@NotNull
	private final CommandManager commands;
	@NotNull
	private final EventManager events;
	@NotNull
	private final RepositoryFactory repositories;
	private JDA jda;

	public Bot() throws SQLException {
		config = new ConfigLoader();
		commands = new CommandManager(this);
		events = new EventManager(this);
		repositories = new RepositoryFactory(this);
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
		commands.registerCommands();
	}
}
