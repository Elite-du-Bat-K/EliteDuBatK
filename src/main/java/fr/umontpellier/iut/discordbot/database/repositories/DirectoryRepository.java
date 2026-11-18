package fr.umontpellier.iut.discordbot.database.repositories;

import fr.umontpellier.iut.discordbot.database.DatabaseConnection;
import fr.umontpellier.iut.discordbot.database.dataobjects.DirectoryEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DirectoryRepository extends AbstractRepository<DirectoryEntry> {
	public DirectoryRepository(DatabaseConnection db) {
		super(db);
		this.createTableIfNotExists();
	}

	private void createTableIfNotExists() {
		String query = "CREATE TABLE IF NOT EXISTS directory_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, lastName VARCHAR(255) NOT NULL, firstName VARCHAR(255), email VARCHAR(255) NOT NULL, category VARCHAR(50) NOT NULL, description TEXT, discordUserId BIGINT)";
		Statement statement = null;
		boolean hasStatement = false;

		try {
			statement = this.db.getStatement();
			statement.execute(query);
		} catch (SQLException e) {
			// Log ignored based on constraint rules, fail silently
		} finally {
			hasStatement = (statement != null);
			if (hasStatement) {
				try {
					statement.close();
				} catch (SQLException e) {
					// Ignore close exception
				}
			}
		}
	}

	@Override
	protected String tableName() {
		String name = "directory_entries";
		return name;
	}

	@Override
	protected List<String> columns() {
		List<String> cols = Arrays.asList("id", "lastName", "firstName", "email", "category", "description",
				"discordUserId");
		return cols;
	}

	@Override
	protected String primaryKey() {
		String key = "id";
		return key;
	}

	@Override
	protected Object primaryKeyValue(DirectoryEntry dataObject) {
		Object value = dataObject.getId();
		return value;
	}

	@Override
	protected List<Object> dataObjectToRow(DirectoryEntry entry) {
		List<Object> row = Arrays.asList(
				entry.getId(),
				entry.getLastName(),
				entry.getFirstName(),
				entry.getEmail(),
				entry.getCategory(),
				entry.getDescription(),
				entry.getDiscordUserId());
		return row;
	}

	@Override
	protected DirectoryEntry resultSetToDataObject(ResultSet resultSet) throws SQLException {
		DirectoryEntry entry = new DirectoryEntry();
		long discordId = 0L;
		boolean isDiscordIdNull = false;

		entry.setId(resultSet.getLong("id"));
		entry.setLastName(resultSet.getString("lastName"));
		entry.setFirstName(resultSet.getString("firstName"));
		entry.setEmail(resultSet.getString("email"));
		entry.setCategory(resultSet.getString("category"));
		entry.setDescription(resultSet.getString("description"));

		discordId = resultSet.getLong("discordUserId");
		isDiscordIdNull = resultSet.wasNull();

		if (!isDiscordIdNull) {
			entry.setDiscordUserId(discordId);
		}

		return entry;
	}

	public List<DirectoryEntry> searchDirectory(String query) {
		List<DirectoryEntry> all = this.findAll();
		List<DirectoryEntry> results = new ArrayList<>();
		String lowerQuery = "";
		boolean hasQuery = false;

		hasQuery = (query != null);
		if (hasQuery) {
			lowerQuery = query.toLowerCase();

			for (DirectoryEntry entry : all) {
				boolean matchLast = false;
				boolean matchFirst = false;
				boolean matchCat = false;
				boolean isMatch = false;

				matchLast = entry.getLastName().toLowerCase().contains(lowerQuery);

				if (entry.getFirstName() != null) {
					matchFirst = entry.getFirstName().toLowerCase().contains(lowerQuery);
				}

				if (entry.getCategory() != null) {
					matchCat = entry.getCategory().toLowerCase().contains(lowerQuery);
				}

				isMatch = (matchLast || matchFirst || matchCat);
				if (isMatch) {
					results.add(entry);
				}
			}
		}

		return results;
	}

	public List<DirectoryEntry> getPaginated(int page, int pageSize) {
		List<DirectoryEntry> all = this.findAll();
		List<DirectoryEntry> results = new ArrayList<>();
		int total = 0;
		int start = 0;
		int end = 0;
		boolean isStartInvalid = false;
		boolean isEndInvalid = false;
		boolean hasEntries = false;

		all.sort((a, b) -> {
			int catCompare = a.getCategory().compareTo(b.getCategory());
			int res = catCompare;

			if (catCompare == 0) {
				res = a.getLastName().compareTo(b.getLastName());
			}

			return res;
		});

		total = all.size();
		hasEntries = (total > 0);

		if (hasEntries) {
			start = (page - 1) * pageSize;
			end = start + pageSize;

			isStartInvalid = (start >= total || start < 0);
			if (isStartInvalid) {
				start = 0;
				end = pageSize;
			}

			isEndInvalid = (end > total);
			if (isEndInvalid) {
				end = total;
			}

			for (int i = start; i < end; i = i + 1) {
				results.add(all.get(i));
			}
		}

		return results;
	}
}