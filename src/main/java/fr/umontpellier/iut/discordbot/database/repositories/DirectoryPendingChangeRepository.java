package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryPendingChange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DirectoryPendingChangeRepository extends AbstractRepository<DirectoryPendingChange> {

	public DirectoryPendingChangeRepository(DatabaseConnection db) {
		super(db);
		this.createTableIfNotExists();
	}

	private void createTableIfNotExists() {
		String query = "CREATE TABLE IF NOT EXISTS directory_pending_changes (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				"operation TEXT NOT NULL, " +
				"target_entry_id INTEGER, " +
				"last_name TEXT, " +
				"first_name TEXT, " +
				"email TEXT, " +
				"category TEXT, " +
				"description TEXT, " +
				"discord_user_id INTEGER, " +
				"requester_id TEXT NOT NULL, " +
				"requested_at INTEGER NOT NULL" +
				")";

		try (Statement statement = this.db.getStatement()) {
			statement.execute(query);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to initialize directory_pending_changes table", e);
		}
	}

	@Override
	protected String tableName() {
		return "directory_pending_changes";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("id", "operation", "target_entry_id", "last_name", "first_name", "email", "category",
				"description", "discord_user_id", "requester_id", "requested_at");
	}

	@Override
	protected String primaryKey() {
		return "id";
	}

	@Override
	protected Object primaryKeyValue(DirectoryPendingChange dataObject) {
		return dataObject.getId();
	}

	@Override
	protected List<Object> dataObjectToRow(DirectoryPendingChange dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getOperation(),
				dataObject.getTargetEntryId(),
				dataObject.getLastName(),
				dataObject.getFirstName(),
				dataObject.getEmail(),
				dataObject.getCategory(),
				dataObject.getDescription(),
				dataObject.getDiscordUserId(),
				dataObject.getRequesterId(),
				dataObject.getRequestedAt());
	}

	@Override
	protected DirectoryPendingChange resultSetToDataObject(ResultSet resultSet) throws SQLException {
		Long targetEntryId = resultSet.getLong("target_entry_id");
		if (resultSet.wasNull()) {
			targetEntryId = null;
		}

		Long discordUserId = resultSet.getLong("discord_user_id");
		if (resultSet.wasNull()) {
			discordUserId = null;
		}

		return new DirectoryPendingChange(
				resultSet.getLong("id"),
				resultSet.getString("operation"),
				targetEntryId,
				resultSet.getString("last_name"),
				resultSet.getString("first_name"),
				resultSet.getString("email"),
				resultSet.getString("category"),
				resultSet.getString("description"),
				discordUserId,
				resultSet.getString("requester_id"),
				resultSet.getLong("requested_at"));
	}

	public List<DirectoryPendingChange> findAllOrdered() {
		return this.query(
				"SELECT * FROM directory_pending_changes ORDER BY requested_at ASC",
				null,
				this::resultSetToDataObject);
	}

	public Optional<DirectoryPendingChange> findLatestByRequester(String requesterId) {
		return this.queryOne(
				"SELECT * FROM directory_pending_changes WHERE requester_id = ? ORDER BY id DESC LIMIT 1",
				statement -> statement.setString(1, requesterId),
				this::resultSetToDataObject);
	}
}