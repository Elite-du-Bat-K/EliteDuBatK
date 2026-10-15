package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public class PollRepository extends AbstractRepository<PollRepository.Poll> {

	public PollRepository(DatabaseConnection db) {
		super(db);
		this.initializeTables();
	}

	private void initializeTables() {
		String createPolls = "CREATE TABLE IF NOT EXISTS polls (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, is_open INTEGER NOT NULL, creator_id TEXT NOT NULL, closes_at INTEGER, restriction_mode TEXT DEFAULT 'NONE')";
		String createPollRoles = "CREATE TABLE IF NOT EXISTS poll_roles (id INTEGER PRIMARY KEY AUTOINCREMENT, poll_id INTEGER NOT NULL, role_id TEXT NOT NULL)";
		String createChoices = "CREATE TABLE IF NOT EXISTS poll_choices (id INTEGER PRIMARY KEY AUTOINCREMENT, poll_id INTEGER NOT NULL, local_index INTEGER NOT NULL DEFAULT 0, choice_text TEXT NOT NULL, votes_count INTEGER NOT NULL)";
		String createVoters = "CREATE TABLE IF NOT EXISTS poll_voters (poll_id INTEGER NOT NULL, user_id TEXT NOT NULL, PRIMARY KEY (poll_id, user_id))";
		String createComments = "CREATE TABLE IF NOT EXISTS poll_comments (id INTEGER PRIMARY KEY AUTOINCREMENT, poll_id INTEGER NOT NULL, user_id TEXT, comment_text TEXT NOT NULL, is_anonymous INTEGER NOT NULL)";

		String checkPollsPragma = "PRAGMA table_info(polls)";
		String alterPollsClosesAt = "ALTER TABLE polls ADD COLUMN closes_at INTEGER";
		String alterPollsRestriction = "ALTER TABLE polls ADD COLUMN restriction_mode TEXT DEFAULT 'NONE'";

		String checkChoicesPragma = "PRAGMA table_info(poll_choices)";
		String alterChoicesTable = "ALTER TABLE poll_choices ADD COLUMN local_index INTEGER NOT NULL DEFAULT 0";
		String updateChoicesIndex = "UPDATE poll_choices SET local_index = (SELECT COUNT(*) FROM poll_choices p2 WHERE p2.poll_id = poll_choices.poll_id AND p2.id <= poll_choices.id)";

		try (Statement stmt = this.db.getStatement()) {
			stmt.execute(createPolls);
			stmt.execute(createPollRoles);
			stmt.execute(createChoices);
			stmt.execute(createVoters);
			stmt.execute(createComments);

			boolean hasClosesAt = false;
			boolean hasRestrictionMode = false;
			try (ResultSet rs = stmt.executeQuery(checkPollsPragma)) {
				boolean hasNext = rs.next();
				while (hasNext) {
					String colName = rs.getString("name");
					boolean isClosesAt = "closes_at".equals(colName);
					boolean isRestrictionMode = "restriction_mode".equals(colName);
					if (isClosesAt) {
						hasClosesAt = true;
					}
					if (isRestrictionMode) {
						hasRestrictionMode = true;
					}
					hasNext = rs.next();
				}
			}
			if (!hasClosesAt) {
				stmt.execute(alterPollsClosesAt);
			}
			if (!hasRestrictionMode) {
				stmt.execute(alterPollsRestriction);
			}

			boolean hasLocalIndex = false;
			try (ResultSet rs = stmt.executeQuery(checkChoicesPragma)) {
				boolean hasNext = rs.next();
				while (hasNext) {
					String colName = rs.getString("name");
					boolean isLocalIndex = "local_index".equals(colName);
					if (isLocalIndex) {
						hasLocalIndex = true;
					}
					hasNext = rs.next();
				}
			}
			if (!hasLocalIndex) {
				stmt.execute(alterChoicesTable);
				stmt.execute(updateChoicesIndex);
			}

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected String tableName() {
		return "polls";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("id", "title", "description", "is_open", "creator_id", "closes_at", "restriction_mode");
	}

	@Override
	protected String primaryKey() {
		return "id";
	}

	@Override
	protected Object primaryKeyValue(Poll dataObject) {
		return dataObject.getId();
	}

	@Override
	protected Poll resultSetToDataObject(ResultSet resultSet) throws SQLException {
		Long closesAt = resultSet.getLong("closes_at");
		boolean wasNull = resultSet.wasNull();
		if (wasNull) {
			closesAt = null;
		}
		String restrictionMode = resultSet.getString("restriction_mode");
		boolean hasRestrictionMode = restrictionMode != null;
		if (!hasRestrictionMode) {
			restrictionMode = "NONE";
		}
		return new Poll(
				resultSet.getLong("id"),
				resultSet.getString("title"),
				resultSet.getString("description"),
				resultSet.getInt("is_open") == 1,
				resultSet.getString("creator_id"),
				closesAt,
				restrictionMode);
	}

	@Override
	protected List<Object> dataObjectToRow(Poll dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getTitle(),
				dataObject.getDescription(),
				dataObject.isOpen() ? 1 : 0,
				dataObject.getCreatorId(),
				dataObject.getClosesAt(),
				dataObject.getRestrictionMode());
	}

	public static class Poll extends AbstractDataObject {
		private final long id;
		private final String title;
		private final String description;
		private final boolean isOpen;
		private final String creatorId;
		private final Long closesAt;
		private final String restrictionMode;

		public Poll(long id, String title, String description, boolean isOpen, String creatorId, Long closesAt,
				String restrictionMode) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.isOpen = isOpen;
			this.creatorId = creatorId;
			this.closesAt = closesAt;
			this.restrictionMode = restrictionMode;
		}

		public long getId() {
			return this.id;
		}

		public String getTitle() {
			return this.title;
		}

		public String getDescription() {
			return this.description;
		}

		public boolean isOpen() {
			return this.isOpen;
		}

		public String getCreatorId() {
			return this.creatorId;
		}

		public Long getClosesAt() {
			return this.closesAt;
		}

		public String getRestrictionMode() {
			return this.restrictionMode;
		}
	}
}