package fr.umontpellier.iut.discordbot;

import java.sql.SQLException;

public class Main {
	public static void main(String[] args) throws SQLException {
		Bot bot = new Bot();
		bot.run();
	}
}