package fr.umontpellier.iut.discordbot;

import fr.umontpellier.iut.discordbot.commands.CommandManager;
import fr.umontpellier.iut.discordbot.config.ConfigLoader;
import fr.umontpellier.iut.discordbot.events.EventManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Bot implements Runnable {
	@NotNull
	private final ConfigLoader config;
	@NotNull
	private final CommandManager commands;
	@NotNull
	private final EventManager events;
	private JDA jda;

	@NotNull
	private final Map<String, Object> cachedMessages;

	public Bot() {
		config = new ConfigLoader();
		commands = new CommandManager(this);
		events = new EventManager(this);
		cachedMessages = new HashMap<>();
	}

	@Override
	public void run() {
		this.jda = JDABuilder.createLight(config.get().getToken(), Collections.emptyList())
				.build();

		events.registerEvents();
		commands.registerCommands();
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
	public JDA getJda() {
		if (jda == null) {
			throw new IllegalStateException("JDA is not initialized yet. Please run the bot first.");
		}
		return jda;
	}

	@NotNull
	public Map<String, Object> getCachedMessages() {
		return cachedMessages;
	}
}
