package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.ChannelLock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing channel lock records.
 * Enforces one active lock per channel (locks are stored with unique channel IDs).
 */
public class ChannelLockRepository extends AbstractRepository<ChannelLock> {

    public ChannelLockRepository(DatabaseConnection db) {
        super(db);
        initializeTable();
    }

    @Override
    protected String tableName() {
        return "channel_locks";
    }

    @Override
    protected List<String> columns() {
        return List.of("id", "channel_id", "guild_id", "locked_by_id", "locked_at", "channel_state_json");
    }

    @Override
    protected String primaryKey() {
        return "id";
    }

    @Override
    protected Object primaryKeyValue(ChannelLock dataObject) {
        return dataObject.getId();
    }

    @Override
    protected ChannelLock resultSetToDataObject(ResultSet resultSet) throws SQLException {
        return new ChannelLock(
                resultSet.getLong("id"),
                resultSet.getString("channel_id"),
                resultSet.getString("guild_id"),
                resultSet.getString("locked_by_id"),
                resultSet.getLong("locked_at"),
                resultSet.getString("channel_state_json")
        );
    }

    @Override
    protected List<Object> dataObjectToRow(ChannelLock dataObject) {
        return List.of(
                dataObject.getId(),
                dataObject.getChannelId(),
                dataObject.getGuildId(),
                dataObject.getLockedById(),
                dataObject.getLockedAt(),
                dataObject.getChannelStateJson()
        );
    }

    /**
     * Initialize the channel_locks table if it doesn't exist.
     */
    private void initializeTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS channel_locks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    channel_id TEXT NOT NULL UNIQUE,
                    guild_id TEXT NOT NULL,
                    locked_by_id TEXT NOT NULL,
                    locked_at INTEGER NOT NULL,
                    channel_state_json TEXT NOT NULL
                )
                """;
        try (Statement stmt = db.getStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize channel_locks table", e);
        }
    }

    /**
     * Find a lock by channel ID.
     * @param channelId The Discord channel ID
     * @return Optional containing the ChannelLock if found
     */
    public Optional<ChannelLock> findByChannelId(String channelId) {
        return queryOne(
                "SELECT * FROM " + tableName() + " WHERE channel_id = ? LIMIT 1",
                statement -> statement.setString(1, channelId),
                this::resultSetToDataObject
        );
    }

    /**
     * Delete a lock by channel ID.
     * @param channelId The Discord channel ID
     * @return The number of rows deleted
     */
    public int deleteByChannelId(String channelId) {
        return update(
                "DELETE FROM " + tableName() + " WHERE channel_id = ?",
                statement -> statement.setString(1, channelId)
        );
    }
}

