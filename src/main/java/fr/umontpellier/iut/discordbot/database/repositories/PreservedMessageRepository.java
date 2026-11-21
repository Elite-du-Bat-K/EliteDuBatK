package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.PreservedMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public class PreservedMessageRepository extends AbstractRepository<PreservedMessage> {

	public PreservedMessageRepository(DatabaseConnection db) {
		super(db);
		this.initializeTable();
	}

	private void initializeTable() {
		String sql = "CREATE TABLE IF NOT EXISTS preserved_messages (" +
				"message_id INTEGER PRIMARY KEY, " +
				"channel_id INTEGER NOT NULL, " +
				"author_id INTEGER NOT NULL, " +
				"content TEXT NOT NULL, " +
				"timestamp INTEGER NOT NULL, " +
				"preserved_by TEXT NOT NULL, " +
				"preserved_at INTEGER NOT NULL, " +
				"reason TEXT NOT NULL" +
				")";

		try (Statement statement = this.db.getStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to initialize preserved_messages table", e);
		}
	}

	@Override
	protected String tableName() {
		return "preserved_messages";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("message_id", "channel_id", "author_id", "content", "timestamp",
				"preserved_by", "preserved_at", "reason");
	}

	@Override
	protected String primaryKey() {
		return "message_id";
	}

	@Override
	protected Object primaryKeyValue(PreservedMessage dataObject) {
		return dataObject.getMessageId();
	}

	@Override
	protected PreservedMessage resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new PreservedMessage(
				resultSet.getLong("message_id"),
				resultSet.getLong("channel_id"),
				resultSet.getLong("author_id"),
				resultSet.getString("content"),
				resultSet.getLong("timestamp"),
				resultSet.getString("preserved_by"),
				resultSet.getLong("preserved_at"),
				resultSet.getString("reason"));
	}

	@Override
	protected List<Object> dataObjectToRow(PreservedMessage dataObject) {
		return Arrays.asList(
				dataObject.getMessageId(),
				dataObject.getChannelId(),
				dataObject.getAuthorId(),
				dataObject.getContent(),
				dataObject.getTimestamp(),
				dataObject.getPreservedBy(),
				dataObject.getPreservedAt(),
				dataObject.getReason());
	}

	public boolean insertIfAbsent(PreservedMessage message) {
		boolean inserted = false;
		boolean isValid = message != null;

		if (isValid) {
			int affected = this.update(
					"INSERT OR IGNORE INTO preserved_messages (message_id, channel_id, author_id, content, timestamp, preserved_by, preserved_at, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
					statement -> {
						statement.setLong(1, message.getMessageId());
						statement.setLong(2, message.getChannelId());
						statement.setLong(3, message.getAuthorId());
						statement.setString(4, message.getContent());
						statement.setLong(5, message.getTimestamp());
						statement.setString(6, message.getPreservedBy());
						statement.setLong(7, message.getPreservedAt());
						statement.setString(8, message.getReason());
					});
			inserted = affected > 0;
		}

		return inserted;
	}

	public List<PreservedMessage> findByAuthorId(long authorId) {
		return this.query(
				"SELECT * FROM preserved_messages WHERE author_id = ? ORDER BY timestamp ASC",
				statement -> statement.setLong(1, authorId),
				this::resultSetToDataObject);
	}
}