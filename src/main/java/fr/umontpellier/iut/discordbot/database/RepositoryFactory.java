package fr.umontpellier.iut.discordbot.database;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.repositories.AbstractRepository;
import fr.umontpellier.iut.discordbot.database.repositories.CachedMessageRepository;

import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class RepositoryFactory {
	private final Bot bot;
	private final DatabaseConnection db;
	private final Map<Class<? extends AbstractRepository<?>>, AbstractRepository<?>> repositories;

	public RepositoryFactory(Bot bot) throws SQLException {
		this.bot = bot;
		this.db = new DatabaseConnection(bot.getConfig().get().getJDBCUrl());
		this.repositories = new HashMap<>();
	}

	public <T extends AbstractRepository<?>> T getRepository(Class<T> repositoryClass) {
		T resolvedRepository = null;
		boolean isCached = this.repositories.containsKey(repositoryClass);

		if (isCached) {
			resolvedRepository = repositoryClass.cast(this.repositories.get(repositoryClass));
		}

		boolean needsInstantiation = !isCached;
		if (needsInstantiation) {
			try {
				try {
					Constructor<T> constructor = repositoryClass.getConstructor(DatabaseConnection.class);
					resolvedRepository = constructor.newInstance(this.db);
				} catch (NoSuchMethodException ignored) {
					Constructor<T> constructor = repositoryClass.getConstructor(Bot.class, DatabaseConnection.class);
					resolvedRepository = constructor.newInstance(this.bot, this.db);
				}
				this.repositories.put(repositoryClass, resolvedRepository);
			} catch (Exception e) {
				throw new RuntimeException("Repository " + repositoryClass.getSimpleName()
						+ " must have a constructor with parameters (DatabaseConnection) or (Bot, DatabaseConnection)",
						e);
			}
		}

		return resolvedRepository;
	}

	public CachedMessageRepository getCachedMessageRepository() {
		return this.getRepository(CachedMessageRepository.class);
	}

	public void close() throws SQLException {
		this.db.close();
	}
}