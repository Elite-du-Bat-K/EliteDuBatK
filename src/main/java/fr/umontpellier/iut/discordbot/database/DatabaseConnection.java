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

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
