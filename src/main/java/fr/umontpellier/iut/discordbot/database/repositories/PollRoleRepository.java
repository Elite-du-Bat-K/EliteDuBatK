package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.AbstractDataObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class PollRoleRepository extends AbstractRepository<PollRoleRepository.PollRole> {

    public PollRoleRepository(DatabaseConnection db) {
        super(db);
    }

    @Override
    protected String tableName() {
        return "poll_roles";
    }

    @Override
    protected List<String> columns() {
        return Arrays.asList("id", "poll_id", "role_id");
    }

    @Override
    protected String primaryKey() {
        return "id";
    }

    @Override
    protected Object primaryKeyValue(PollRole dataObject) {
        return dataObject.getId();
    }

    @Override
    protected PollRole resultSetToDataObject(ResultSet resultSet) throws SQLException {
        return new PollRole(
                resultSet.getLong("id"),
                resultSet.getLong("poll_id"),
                resultSet.getString("role_id"));
    }

    @Override
    protected List<Object> dataObjectToRow(PollRole dataObject) {
        return Arrays.asList(
                dataObject.getId(),
                dataObject.getPollId(),
                dataObject.getRoleId());
    }

    public List<PollRole> findByPollId(long pollId) {
        return this.query(
                "SELECT * FROM poll_roles WHERE poll_id = ?",
                statement -> statement.setLong(1, pollId),
                this::resultSetToDataObject);
    }

    public boolean deleteByPollIdAndRoleId(long pollId, String roleId) {
        boolean isDeleted = false;
        int affectedRows = this.update(
                "DELETE FROM poll_roles WHERE poll_id = ? AND role_id = ?",
                statement -> {
                    statement.setLong(1, pollId);
                    statement.setString(2, roleId);
                });
        boolean hasAffected = affectedRows > 0;
        if (hasAffected) {
            isDeleted = true;
        }
        return isDeleted;
    }

    public boolean deleteByPollId(long pollId) {
        boolean isDeleted = false;
        int affectedRows = this.update(
                "DELETE FROM poll_roles WHERE poll_id = ?",
                statement -> statement.setLong(1, pollId));
        boolean hasAffected = affectedRows > 0;
        if (hasAffected) {
            isDeleted = true;
        }
        return isDeleted;
    }

    public static class PollRole extends AbstractDataObject {
        private final long id;
        private final long pollId;
        private final String roleId;

        public PollRole(long id, long pollId, String roleId) {
            this.id = id;
            this.pollId = pollId;
            this.roleId = roleId;
        }

        public long getId() {
            return this.id;
        }

        public long getPollId() {
            return this.pollId;
        }

        public String getRoleId() {
            return this.roleId;
        }
    }
}