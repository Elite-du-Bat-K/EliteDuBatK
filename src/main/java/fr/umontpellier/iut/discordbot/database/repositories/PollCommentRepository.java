package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PollCommentRepository extends AbstractRepository<PollCommentRepository.PollComment> {

	public PollCommentRepository(DatabaseConnection db) {
		super(db);
	}

	@Override
	protected String tableName() {
		return "poll_comments";
	}

	@Override
	protected List<String> columns() {
		return Arrays.asList("id", "poll_id", "user_id", "comment_text", "is_anonymous");
	}

	@Override
	protected String primaryKey() {
		return "id";
	}

	@Override
	protected Object primaryKeyValue(PollComment dataObject) {
		return dataObject.getId();
	}

	@Override
	protected PollComment resultSetToDataObject(ResultSet resultSet) throws SQLException {
		return new PollComment(
				resultSet.getLong("id"),
				resultSet.getLong("poll_id"),
				resultSet.getString("user_id"),
				resultSet.getString("comment_text"),
				resultSet.getInt("is_anonymous") == 1);
	}

	@Override
	protected List<Object> dataObjectToRow(PollComment dataObject) {
		return Arrays.asList(
				dataObject.getId(),
				dataObject.getPollId(),
				dataObject.getUserId(),
				dataObject.getCommentText(),
				dataObject.isAnonymous() ? 1 : 0);
	}

	public List<PollComment> findByPollId(long pollId) {
		return this.query(
				"SELECT * FROM poll_comments WHERE poll_id = ?",
				statement -> statement.setLong(1, pollId),
				this::resultSetToDataObject);
	}

	public static class PollComment extends AbstractDataObject {
		private final long id;
		private final long pollId;
		private final String userId;
		private final String commentText;
		private final boolean isAnonymous;

		public PollComment(long id, long pollId, String userId, String commentText, boolean isAnonymous) {
			this.id = id;
			this.pollId = pollId;
			this.userId = userId;
			this.commentText = commentText;
			this.isAnonymous = isAnonymous;
		}

		public long getId() {
			return this.id;
		}

		public long getPollId() {
			return this.pollId;
		}

		public String getUserId() {
			return this.userId;
		}

		public String getCommentText() {
			return this.commentText;
		}

		public boolean isAnonymous() {
			return this.isAnonymous;
		}
	}
}