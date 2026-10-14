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
		String createPolls = "CREATE TABLE IF NOT EXISTS polls (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, is_open INTEGER NOT NULL, creator_id TEXT NOT NULL)";
		String createChoices = "CREATE TABLE IF NOT EXISTS poll_choices (id INTEGER PRIMARY KEY AUTOINCREMENT, poll_id INTEGER NOT NULL, choice_text TEXT NOT NULL, votes_count INTEGER NOT NULL)";
		String createVoters = "CREATE TABLE IF NOT EXISTS poll_voters (poll_id INTEGER NOT NULL, user_id TEXT NOT NULL, PRIMARY KEY (poll_id, user_id))";
		String createComments = "CREATE TABLE IF NOT EXISTS poll_comments (id INTEGER PRIMARY KEY AUTOINCREMENT, poll_id INTEGER NOT NULL, user_id TEXT, comment_text TEXT NOT NULL, is_anonymous INTEGER NOT NULL)";

		boolean success = false;
		try (Statement stmt = this.db.getStatement()) {
			stmt.execute(createPolls);
			stmt.execute(createChoices);
			stmt.execute(createVoters);
			stmt.execute(createComments);
			success = true;
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
		return Arrays.asList("id", "title", "description", "is_open", "creator_id");
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
		return new Poll(
				resultSet.getLong("id"),
				resultSet.getString("title"),
				resultSet.getString("description"),
				resultSet.getInt("is_open") == 1,
				resultSet.getString("creator_id"));
	}

	@Override
	protected List<Object> dataObjectToRow(Poll dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getTitle(),
				dataObject.getDescription(),
				dataObject.isOpen() ? 1 : 0,
				dataObject.getCreatorId());
	}

	public static class Poll extends AbstractDataObject {
		private final long id;
		private final String title;
		private final String description;
		private final boolean isOpen;
		private final String creatorId;

		public Poll(long id, String title, String description, boolean isOpen, String creatorId) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.isOpen = isOpen;
			this.creatorId = creatorId;
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
	}
}