package fr.umontpellier.iut.discordbot.database;

import org.jetbrains.annotations.NotNull;
import java.sql.*;

public class DatabaseConnection implements AutoCloseable {
	private final Connection conn;

	public DatabaseConnection(@NotNull String jdbcUrl) throws SQLException {
		this.conn = DriverManager.getConnection(jdbcUrl);
	}

	public Statement getStatement() throws SQLException {
		return conn.createStatement();
	}

	public PreparedStatement getPreparedStatement(String sql) throws SQLException {
		return conn.prepareStatement(sql);
	}

	public void vacuum() throws SQLException {
		try (Statement statement = this.conn.createStatement()) {
			statement.execute("VACUUM");
		}
	}

	@Override
	public void close() throws SQLException {
		conn.close();
	}
}