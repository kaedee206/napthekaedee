package com.kaedee.napthe.commands;

import com.kaedee.napthe.NapThe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class NapInfoCommand implements CommandExecutor {

    private final NapThe plugin;

    public NapInfoCommand(NapThe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = plugin.getSQLiteManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE uuid = ?");
                 PreparedStatement psTx = conn.prepareStatement("SELECT * FROM transactions WHERE uuid = ? ORDER BY timestamp DESC LIMIT 5")) {
                 
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    double total = rs.getDouble("total_donated");
                    String ip = rs.getString("ip_address");
                    
                    // Simple rank logic based on total (should match reward.yml theoretically, but keeping simple here)
                    String rank = "Mặc định";
                    if (total >= 1000000) rank = "Rank 5";
                    else if (total >= 750000) rank = "Rank 4";
                    else if (total >= 500000) rank = "Rank 3";
                    else if (total >= 200000) rank = "Rank 2";
                    else if (total >= 100000) rank = "Rank 1";
                    
                    player.sendMessage("§e=== THÔNG TIN NẠP THẺ ===");
                    player.sendMessage("§f[ §d" + rank + " §f] - §a" + player.getName());
                    player.sendMessage("§7Tổng nạp: §e" + (int)total + " VND");
                    if (player.hasPermission("napthe.admin")) {
                        player.sendMessage("§7IP Address: §e" + ip);
                    }
                    
                    player.sendMessage("§75 Giao dịch gần nhất:");
                    psTx.setString(1, player.getUniqueId().toString());
                    ResultSet rsTx = psTx.executeQuery();
                    
                    while (rsTx.next()) {
                        String type = rsTx.getString("type");
                        double amount = rsTx.getDouble("amount_vnd");
                        String status = rsTx.getString("status");
                        String time = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(rsTx.getLong("timestamp")));
                        
                        String color = status.equals("SUCCESS") || status.equals("SUCCESS_PENALTY") ? "§a" : (status.equals("PENDING") ? "§e" : "§c");
                        player.sendMessage("§8- §7" + time + " §8| §f" + type + " §8| §b" + (int)amount + "đ §8| " + color + status);
                    }
                    
                    // Sending clickable text for full history using json raw message is possible, but plain text for now
                    player.sendMessage("");
                    player.sendMessage("§aGõ §e/napthe history §ađể xem toàn bộ lịch sử nạp.");
                    player.sendMessage("§e=========================");
                } else {
                    player.sendMessage("§cBạn chưa có thông tin giao dịch nào.");
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("§cLỗi khi tải thông tin.");
            }
        });
        
        return true;
    }
}
