package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.GdprDeletionRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GdprDeletionRepository extends AbstractRepository<GdprDeletionRequest> {

	public GdprDeletionRepository(DatabaseConnection db) {
		super(db);
		this.initializeTable();
	}

	private void initializeTable() {
		String sql = "CREATE TABLE IF NOT EXISTS gdpr_deletion_requests (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				"user_id TEXT NOT NULL, " +
				"requested_at INTEGER NOT NULL, " +
				"execute_at INTEGER NOT NULL, " +
				"status TEXT NOT NULL, " +
				"reviewed_by TEXT, " +
				"refusal_reason TEXT" +
				")";

		try (Statement statement = this.db.getStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to initialize gdpr_deletion_requests table", e);
		}
	}

	@Override
	protected String tableName() {
		return "gdpr_deletion_requests";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("id", "user_id", "requested_at", "execute_at", "status", "reviewed_by", "refusal_reason");
	}

	@Override
	protected String primaryKey() {
		return "id";
	}

	@Override
	protected Object primaryKeyValue(GdprDeletionRequest dataObject) {
		return dataObject.getId();
	}

	@Override
	protected GdprDeletionRequest resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new GdprDeletionRequest(
				resultSet.getLong("id"),
				resultSet.getString("user_id"),
				resultSet.getLong("requested_at"),
				resultSet.getLong("execute_at"),
				resultSet.getString("status"),
				resultSet.getString("reviewed_by"),
				resultSet.getString("refusal_reason"));
	}

	@Override
	protected List<Object> dataObjectToRow(GdprDeletionRequest dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getUserId(),
				dataObject.getRequestedAt(),
				dataObject.getExecuteAt(),
				dataObject.getStatus(),
				dataObject.getReviewedBy(),
				dataObject.getRefusalReason());
	}

	public Optional<GdprDeletionRequest> findPendingByUserId(String userId) {
		return this.queryOne(
				"SELECT * FROM gdpr_deletion_requests WHERE user_id = ? AND status = ? LIMIT 1",
				statement -> {
					statement.setString(1, userId);
					statement.setString(2, GdprDeletionRequest.STATUS_PENDING);
				},
				this::resultSetToDataObject);
	}

	public List<GdprDeletionRequest> findAllPending() {
		return this.query(
				"SELECT * FROM gdpr_deletion_requests WHERE status = ? ORDER BY execute_at ASC",
				statement -> statement.setString(1, GdprDeletionRequest.STATUS_PENDING),
				this::resultSetToDataObject);
	}

	public List<GdprDeletionRequest> findDuePending(long nowMillis) {
		return this.query(
				"SELECT * FROM gdpr_deletion_requests WHERE status = ? AND execute_at <= ? ORDER BY execute_at ASC",
				statement -> {
					statement.setString(1, GdprDeletionRequest.STATUS_PENDING);
					statement.setLong(2, nowMillis);
				},
				this::resultSetToDataObject);
	}
}