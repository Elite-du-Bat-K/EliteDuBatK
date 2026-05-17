package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractRepositoryTest {

    @Test
    void abstractRepositorySupportsBasicJdbcCrud() throws Exception {
        try (DatabaseConnection db = new DatabaseConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = db.getStatement()) {
                statement.executeUpdate("CREATE TABLE todos (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
            }

            TodoRepository repository = new TodoRepository(db);
            repository.insert(new TodoEntry(null, "first"));

            assertEquals(1, repository.findAll().size());
            assertTrue(repository.findByPrimaryKey(1L).isPresent());
            assertEquals("first", repository.findByPrimaryKey(1L).orElseThrow().name());

            assertEquals(1, repository.update(new TodoEntry(1L, "updated")));
            Optional<TodoEntry> updated = repository.findByPrimaryKey(1L);
            assertTrue(updated.isPresent());
            assertEquals("updated", updated.orElseThrow().name());

            assertEquals(1, repository.deleteByPrimaryKey(1L));
            assertTrue(repository.findAll().isEmpty());
            assertFalse(repository.findByPrimaryKey(1L).isPresent());
        }
    }

    private static final class TodoRepository extends AbstractRepository<TodoEntry> {
        private TodoRepository(DatabaseConnection db) {
            super(db);
        }

        @Override
        protected String tableName() {
            return "todos";
        }

        @Override
        protected List<String> columns() {
            return List.of("name");
        }

        @Override
        protected String primaryKey() {
            return "id";
        }

        @Override
        protected Object primaryKeyValue(TodoEntry dataObject) {
            return dataObject.id();
        }

        @Override
        protected TodoEntry resultSetToDataObject(ResultSet resultSet) throws SQLException {
            return new TodoEntry(resultSet.getLong("id"), resultSet.getString("name"));
        }

        @Override
        protected List<Object> dataObjectToRow(TodoEntry dataObject) {
            return List.of(dataObject.name());
        }
    }

    private static final class TodoEntry extends AbstractDataObject {
        private final Long id;
        private final String name;

        private TodoEntry(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private Long id() {
            return id;
        }

        private String name() {
            return name;
        }
    }
}


