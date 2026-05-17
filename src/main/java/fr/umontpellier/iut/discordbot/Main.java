package fr.umontpellier.iut.discordbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		try {
			Bot bot = new Bot();
			bot.run();
		} catch (Exception e) {
			logger.error("Failed to start bot", e);
			System.exit(1);
		}
	}
}