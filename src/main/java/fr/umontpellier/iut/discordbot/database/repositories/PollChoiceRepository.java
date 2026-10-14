package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

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
		return Arrays.asList("id", "poll_id", "choice_text", "votes_count");
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
				resultSet.getString("choice_text"),
				resultSet.getLong("votes_count"));
	}

	@Override
	protected List<Object> dataObjectToRow(PollChoice dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getPollId(),
				dataObject.getChoiceText(),
				dataObject.getVotesCount());
	}

	public List<PollChoice> findByPollId(long pollId) {
		return this.query(
				"SELECT * FROM poll_choices WHERE poll_id = ?",
				statement -> statement.setLong(1, pollId),
				this::resultSetToDataObject);
	}

	public static class PollChoice extends AbstractDataObject {
		private final long id;
		private final long pollId;
		private final String choiceText;
		private final long votesCount;

		public PollChoice(long id, long pollId, String choiceText, long votesCount) {
			this.id = id;
			this.pollId = pollId;
			this.choiceText = choiceText;
			this.votesCount = votesCount;
		}

		public long getId() {
			return this.id;
		}

		public long getPollId() {
			return this.pollId;
		}

		public String getChoiceText() {
			return this.choiceText;
		}

		public long getVotesCount() {
			return this.votesCount;
		}
	}
}