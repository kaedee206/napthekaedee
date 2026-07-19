package com.kaedee.napthe.commands;

import com.kaedee.napthe.NapThe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TopNapCommand implements CommandExecutor {

    private final NapThe plugin;

    public TopNapCommand(NapThe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§e--- Top 10 Phú Hộ ---");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = plugin.getSQLiteManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                             "SELECT name, total_donated FROM users ORDER BY total_donated DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {

                int rank = 1;
                while (rs.next()) {
                    String name = rs.getString("name");
                    double total = rs.getDouble("total_donated");
                    sender.sendMessage("§6" + rank + ". §e" + name + " §7- §a" + (int) total + " VND");
                    rank++;
                }

                if (rank == 1) {
                    sender.sendMessage("§cChưa có ai nạp thẻ!");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§cLỗi khi tải top nạp.");
            }
        });
        return true;
    }
}
