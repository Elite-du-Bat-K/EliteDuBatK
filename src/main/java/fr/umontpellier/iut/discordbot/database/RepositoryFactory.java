package fr.umontpellier.iut.discordbot.database;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.repositories.AbstractRepository;


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

    public <T extends AbstractRepository<?>> T getRepository(Class<T> repository) {
        AbstractRepository<?> repo = repositories.get(repository);
        if (repo == null) {
            try {
                try {
                    Constructor<T> constructor = repository.getConstructor(DatabaseConnection.class);
                    repo = constructor.newInstance(db);
                } catch (NoSuchMethodException ignored) {
                    Constructor<T> constructor = repository.getConstructor(Bot.class, DatabaseConnection.class);
                    repo = constructor.newInstance(bot, db);
                }
                repositories.put(repository, repo);
                repo = repositories.get(repository);
            } catch (Exception e) {
                throw new RuntimeException("Repository " + repository.getSimpleName() + " must have a constructor with parameters (DatabaseConnection) or (Bot, DatabaseConnection)", e);
            }
        }
        return repository.cast(repo);
    }

    public void close() throws SQLException {
        db.close();
    }
}
