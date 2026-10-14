package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PollVoterRepository extends AbstractRepository<PollVoterRepository.PollVoter> {

	public PollVoterRepository(DatabaseConnection db) {
		super(db);
	}

	@Override
	protected String tableName() {
		return "poll_voters";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("poll_id", "user_id");
	}

	@Override
	protected String primaryKey() {
		return "poll_id";
	}

	@Override
	protected Object primaryKeyValue(PollVoter dataObject) {
		return dataObject.getPollId();
	}

	@Override
	protected PollVoter resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new PollVoter(
				resultSet.getLong("poll_id"),
				resultSet.getString("user_id"));
	}

	@Override
	protected List<Object> dataObjectToRow(PollVoter dataObject) {
		return Arrays.asList(
				dataObject.getPollId(),
				dataObject.getUserId());
	}

	@Override
	public void insert(PollVoter dataObject) {
		this.update(
				"INSERT INTO poll_voters (poll_id, user_id) VALUES (?, ?)",
				statement -> {
					statement.setLong(1, dataObject.getPollId());
					statement.setString(2, dataObject.getUserId());
				});
	}

	public boolean recordVoteAttempt(long pollId, String userId) {
		boolean voteRegistered = false;
		int affectedRows = this.update(
				"INSERT OR IGNORE INTO poll_voters (poll_id, user_id) VALUES (?, ?)",
				statement -> {
					statement.setLong(1, pollId);
					statement.setString(2, userId);
				});
		boolean isAffected = affectedRows > 0;
		if (isAffected) {
			voteRegistered = true;
		}
		return voteRegistered;
	}

	public boolean hasVoted(long pollId, String userId) {
		boolean hasVotedStatus = false;
		Optional<PollVoter> record = this.queryOne(
				"SELECT * FROM poll_voters WHERE poll_id = ? AND user_id = ? LIMIT 1",
				statement -> {
					statement.setLong(1, pollId);
					statement.setString(2, userId);
				},
				this::resultSetToDataObject);
		boolean isPresent = record.isPresent();
		if (isPresent) {
			hasVotedStatus = true;
		}
		return hasVotedStatus;
	}

	public List<PollVoter> findByPollId(long pollId) {
		return this.query(
				"SELECT * FROM poll_voters WHERE poll_id = ?",
				statement -> statement.setLong(1, pollId),
				this::resultSetToDataObject);
	}

	public static class PollVoter extends AbstractDataObject {
		private final long pollId;
		private final String userId;

		public PollVoter(long pollId, String userId) {
			this.pollId = pollId;
			this.userId = userId;
		}

		public long getPollId() {
			return this.pollId;
		}

		public String getUserId() {
			return this.userId;
		}
	}
}