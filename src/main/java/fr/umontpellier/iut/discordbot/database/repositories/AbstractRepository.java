package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractRepository<DO extends AbstractDataObject> {
    protected final DatabaseConnection db;

    public AbstractRepository(DatabaseConnection db) {
        this.db = db;
    }

    /**
     * @return the table name
     */
    protected abstract String tableName();

    /**
     * @return all columns, including the primary key
     */
    protected abstract List<String> columns();

    /**
     * @return the primary key
     */
    protected abstract String primaryKey();

    protected abstract Object primaryKeyValue(DO dataObject);

    protected abstract DO resultSetToDataObject(ResultSet resultSet) throws SQLException;

    protected abstract List<Object> dataObjectToRow(DO dataObject);

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    protected interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    protected <T> List<T> query(String sql, StatementBinder binder, ResultSetMapper<T> mapper) {
        try (PreparedStatement statement = db.getPreparedStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query on table " + tableName(), e);
        }
    }

    protected int update(String sql, StatementBinder binder) {
        try (PreparedStatement statement = db.getPreparedStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute update on table " + tableName(), e);
        }
    }

    protected <T> Optional<T> queryOne(String sql, StatementBinder binder, ResultSetMapper<T> mapper) {
        try (PreparedStatement statement = db.getPreparedStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapper.map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query on table " + tableName(), e);
        }
    }

    public List<DO> findAll() {
        return query("SELECT * FROM " + tableName(), null, this::resultSetToDataObject);
    }

    public Optional<DO> findByPrimaryKey(Object value) {
        return queryOne(
                "SELECT * FROM " + tableName() + " WHERE " + primaryKey() + " = ? LIMIT 1",
                statement -> statement.setObject(1, value),
                this::resultSetToDataObject
        );
    }

    public void insert(DO dataObject) {
        List<String> columns = columns();
        List<Object> values = dataObjectToRow(dataObject);
        String sql;

        if (columns.isEmpty()) {
            sql = String.format("INSERT INTO %s DEFAULT VALUES", tableName());
        } else {
            String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
            sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName(), String.join(", ", columns), placeholders);
        }

        if (!columns.isEmpty() && columns.size() != values.size()) {
            throw new IllegalStateException("Column count and value count do not match for table " + tableName());
        }

        update(sql, statement -> {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
        });
    }

    public int update(DO dataObject) {
        List<String> columns = columns();
        List<Object> values = dataObjectToRow(dataObject);

        if (columns.isEmpty()) {
            throw new IllegalStateException("Cannot update table " + tableName() + " without updatable columns");
        }
        if (columns.size() != values.size()) {
            throw new IllegalStateException("Column count and value count do not match for table " + tableName());
        }

        String assignments = String.join(", ", columns.stream().map(column -> column + " = ?").toList());
        String sql = String.format("UPDATE %s SET %s WHERE %s = ?", tableName(), assignments, primaryKey());
        Object primaryKeyValue = primaryKeyValue(dataObject);

        return update(sql, statement -> {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            statement.setObject(values.size() + 1, primaryKeyValue);
        });
    }

    public int deleteByPrimaryKey(Object value) {
        return update(
                "DELETE FROM " + tableName() + " WHERE " + primaryKey() + " = ?",
                statement -> statement.setObject(1, value)
        );
    }
}
