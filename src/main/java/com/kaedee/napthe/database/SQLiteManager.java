package com.kaedee.napthe.database;

import com.kaedee.napthe.NapThe;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteManager {

    private final NapThe plugin;
    private Connection connection;
    private final String dbName = "napthe.db";

    public SQLiteManager(NapThe plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, dbName);
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create database file!", e);
            }
        }
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite!", e);
        }
    }

    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16), " +
                    "ip_address VARCHAR(45), " +
                    "total_donated DOUBLE DEFAULT 0, " +
                    "current_crystal DOUBLE DEFAULT 0" +
                    ")");

            statement.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "uuid VARCHAR(36), " +
                    "type VARCHAR(16), " +
                    "amount_vnd DOUBLE, " +
                    "amount_crystal DOUBLE, " +
                    "status VARCHAR(16), " +
                    "timestamp BIGINT" +
                    ")");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create tables!", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void updatePlayerIPAndName(UUID uuid, String name, String ip) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO users (uuid, name, ip_address) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = ?, ip_address = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, ip);
                ps.setString(4, name);
                ps.setString(5, ip);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void addTransaction(String id, UUID uuid, String type, double amountVnd, double amountCrystal, String status) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO transactions (id, uuid, type, amount_vnd, amount_crystal, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, uuid.toString());
                ps.setString(3, type);
                ps.setDouble(4, amountVnd);
                ps.setDouble(5, amountCrystal);
                ps.setString(6, status);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateTransactionStatus(String id, String status) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE transactions SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setString(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void addCrystalAndTotalDonated(UUID uuid, double crystalAmount, double vndAmount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE users SET current_crystal = current_crystal + ?, total_donated = total_donated + ? WHERE uuid = ?")) {
                ps.setDouble(1, crystalAmount);
                ps.setDouble(2, vndAmount);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    public double getTotalDonated(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT total_donated FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total_donated");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    public UUID getUuidByTxId(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM transactions WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
