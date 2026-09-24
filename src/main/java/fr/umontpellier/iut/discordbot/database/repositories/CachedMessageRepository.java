package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.CachedMessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CachedMessageRepository extends AbstractRepository<CachedMessageData> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CachedMessageRepository.class);

	public CachedMessageRepository(DatabaseConnection db) {
		super(db);
	}

	@Override
	protected String tableName() {
		return "cached_messages";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("message_id", "channel_id", "author_id", "content", "timestamp");
	}

	@Override
	protected String primaryKey() {
		return "message_id";
	}

	@Override
	protected Object primaryKeyValue(CachedMessageData dataObject) {
		return dataObject.getMessageId();
	}

	@Override
	protected CachedMessageData resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new CachedMessageData(
				resultSet.getLong("message_id"),
				resultSet.getLong("channel_id"),
				resultSet.getLong("author_id"),
				resultSet.getString("content"),
				resultSet.getLong("timestamp"));
	}

	@Override
	protected List<Object> dataObjectToRow(CachedMessageData dataObject) {
		return Arrays.asList(
				dataObject.getMessageId(),
				dataObject.getChannelId(),
				dataObject.getAuthorId(),
				dataObject.getContent(),
				dataObject.getTimestamp());
	}

	public boolean createTableIfNotExists() {
		boolean success = false;

		try (Statement statement = db.getStatement()) {
			statement.execute("CREATE TABLE IF NOT EXISTS cached_messages (" +
					"message_id INTEGER PRIMARY KEY, " +
					"channel_id INTEGER NOT NULL, " +
					"author_id INTEGER NOT NULL, " +
					"content TEXT NOT NULL, " +
					"timestamp INTEGER NOT NULL" +
					")");

			success = true;
		} catch (SQLException e) {
			LOGGER.error("Error creating cached_messages table", e);
		}

		return success;
	}

	public boolean insertBatch(List<CachedMessageData> messages) {
		boolean success = false;
		boolean hasMessages = messages != null && !messages.isEmpty();

		if (hasMessages) {
			try (PreparedStatement preparedStatement = db.getPreparedStatement(
					"INSERT OR IGNORE INTO cached_messages (message_id, channel_id, author_id, content, timestamp) VALUES (?, ?, ?, ?, ?)")) {

				Connection connection = preparedStatement.getConnection();
				connection.setAutoCommit(false);

				for (CachedMessageData msg : messages) {
					preparedStatement.setLong(1, msg.getMessageId());
					preparedStatement.setLong(2, msg.getChannelId());
					preparedStatement.setLong(3, msg.getAuthorId());
					preparedStatement.setString(4, msg.getContent());
					preparedStatement.setLong(5, msg.getTimestamp());
					preparedStatement.addBatch();
				}

				preparedStatement.executeBatch();
				connection.commit();
				connection.setAutoCommit(true);

				success = true;
			} catch (SQLException e) {
				LOGGER.error("Error inserting batch of cached messages", e);
			}
		}

		return success;
	}

	public List<CachedMessageData> fetchRecent(int limit) {
		List<CachedMessageData> results = new ArrayList<>();
		boolean isValidLimit = limit > 0;

		if (isValidLimit) {
			try (PreparedStatement preparedStatement = db.getPreparedStatement(
					"SELECT message_id, channel_id, author_id, content, timestamp FROM cached_messages ORDER BY timestamp DESC LIMIT ?")) {

				preparedStatement.setInt(1, limit);

				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					while (resultSet.next()) {
						results.add(this.resultSetToDataObject(resultSet));
					}
				}
			} catch (SQLException e) {
				LOGGER.error("Error fetching recent cached messages", e);
			}
		}

		return results;
	}

	public boolean deleteOlderThan(long thresholdTimestamp) {
		boolean success = false;
		boolean isValidThreshold = thresholdTimestamp > 0;

		if (isValidThreshold) {
			try (PreparedStatement preparedStatement = db.getPreparedStatement(
					"DELETE FROM cached_messages WHERE timestamp < ?")) {

				preparedStatement.setLong(1, thresholdTimestamp);
				preparedStatement.executeUpdate();

				success = true;
			} catch (SQLException e) {
				LOGGER.error("Error deleting old cached messages", e);
			}
		}

		return success;
	}
}