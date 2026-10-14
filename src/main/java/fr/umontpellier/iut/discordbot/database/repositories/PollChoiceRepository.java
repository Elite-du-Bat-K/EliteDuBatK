package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PollChoiceRepository extends AbstractRepository<PollChoiceRepository.PollChoice> {

	public PollChoiceRepository(DatabaseConnection db) {
		super(db);
	}

	@Override
	protected String tableName() {
		return "poll_choices";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("id", "poll_id", "local_index", "choice_text", "votes_count");
	}

	@Override
	protected String primaryKey() {
		return "id";
	}

	@Override
	protected Object primaryKeyValue(PollChoice dataObject) {
		return dataObject.getId();
	}

	@Override
	protected PollChoice resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new PollChoice(
				resultSet.getLong("id"),
				resultSet.getLong("poll_id"),
				resultSet.getLong("local_index"),
				resultSet.getString("choice_text"),
				resultSet.getLong("votes_count"));
	}

	@Override
	protected List<Object> dataObjectToRow(PollChoice dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getPollId(),
				dataObject.getLocalIndex(),
				dataObject.getChoiceText(),
				dataObject.getVotesCount());
	}

	public boolean incrementVote(long pollId, long localIndex) {
		int affectedRows = this.update(
				"UPDATE poll_choices SET votes_count = votes_count + 1 WHERE poll_id = ? AND local_index = ?",
				statement -> {
					statement.setLong(1, pollId);
					statement.setLong(2, localIndex);
				});
		return affectedRows > 0;
	}

	public List<PollChoice> findByPollId(long pollId) {
		return this.query(
				"SELECT * FROM poll_choices WHERE poll_id = ?",
				statement -> statement.setLong(1, pollId),
				this::resultSetToDataObject);
	}

	public Optional<PollChoice> findByPollIdAndLocalIndex(long pollId, long localIndex) {
		return this.queryOne(
				"SELECT * FROM poll_choices WHERE poll_id = ? AND local_index = ? LIMIT 1",
				statement -> {
					statement.setLong(1, pollId);
					statement.setLong(2, localIndex);
				},
				this::resultSetToDataObject);
	}

	public static class PollChoice extends AbstractDataObject {
		private final long id;
		private final long pollId;
		private final long localIndex;
		private final String choiceText;
		private final long votesCount;

		public PollChoice(long id, long pollId, long localIndex, String choiceText, long votesCount) {
			this.id = id;
			this.pollId = pollId;
			this.localIndex = localIndex;
			this.choiceText = choiceText;
			this.votesCount = votesCount;
		}

		public long getId() {
			return this.id;
		}

		public long getPollId() {
			return this.pollId;
		}

		public long getLocalIndex() {
			return this.localIndex;
		}

		public String getChoiceText() {
			return this.choiceText;
		}

		public long getVotesCount() {
			return this.votesCount;
		}
	}
}