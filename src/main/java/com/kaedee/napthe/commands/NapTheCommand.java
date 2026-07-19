package com.kaedee.napthe.commands;

import com.kaedee.napthe.NapThe;
import com.kaedee.napthe.map.QRMapRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class NapTheCommand implements CommandExecutor {

    private final NapThe plugin;

    public NapTheCommand(NapThe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "card":
                handleCard(player, args);
                break;
            case "qr":
                handleQR(player, args);
                break;
            case "history":
                handleHistory(player);
                break;
            case "adhistory":
                handleAdHistory(player, args);
                break;
            case "reload":
                handleReload(player);
                break;
            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void sendUsage(Player player) {
        String prefix = plugin.getConfigManager().getMessage("prefix");
        player.sendMessage(prefix + "§eCác lệnh khả dụng:");
        player.sendMessage("  §a/napthe card <amount> <telco> <code> <serial> §7- Nạp thẻ cào");
        player.sendMessage("  §a/napthe qr <amount> §7- Tạo mã QR chuyển khoản");
        player.sendMessage("  §a/napthe history §7- Xem lịch sử nạp");
        if (player.hasPermission("napthe.admin")) {
            player.sendMessage("  §a/napthe adhistory [player|all] [page] §7- Xem lịch sử nạp");
            player.sendMessage("  §a/napthe reload §7- Tải lại cấu hình");
        }
    }

    private void handleCard(Player player, String[] args) {
        String prefix = plugin.getConfigManager().getMessage("prefix");

        if (args.length < 5) {
            player.sendMessage(prefix + "§cSử dụng: §f/napthe card <amount> <telco> <code> <serial>");
            player.sendMessage(prefix + "§7Ví dụ: /napthe card 50k VIETTEL 123456789 987654321");
            return;
        }

        int amount = parseAmount(args[1]);
        if (amount <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("invalid_amount"));
            return;
        }

        String telco = args[2].toUpperCase();
        String code = args[3];
        String serial = args[4];

        String requestId = UUID.randomUUID().toString();

        plugin.getTsrManager().sendCard(player.getUniqueId(), telco, code, serial, amount, requestId);
        player.sendMessage(prefix + plugin.getConfigManager().getMessage("success_card"));
    }

    private void handleQR(Player player, String[] args) {
        String prefix = plugin.getConfigManager().getMessage("prefix");

        if (args.length < 2) {
            player.sendMessage(prefix + "§cSử dụng: §f/napthe qr <amount>");
            player.sendMessage(prefix + "§7Ví dụ: /napthe qr 50k");
            return;
        }

        int amount = parseAmount(args[1]);
        if (amount <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("invalid_amount"));
            return;
        }

        generateQR(player, amount);
        player.sendMessage(prefix + plugin.getConfigManager().getMessage("success_qr"));
    }

    private void handleHistory(Player player) {
        String prefix = plugin.getConfigManager().getMessage("prefix");
        player.sendMessage(prefix + "§e--- Lịch sử nạp của bạn ---");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = plugin.getSQLiteManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                         "SELECT type, amount_vnd, amount_crystal, status, timestamp FROM transactions WHERE uuid = ? ORDER BY timestamp DESC LIMIT 10")) {

                ps.setString(1, player.getUniqueId().toString());

                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        String type = rs.getString("type");
                        int amount = rs.getInt("amount_vnd");
                        double crystal = rs.getDouble("amount_crystal");
                        String status = rs.getString("status");
                        long ts = rs.getLong("timestamp");
                        String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(ts));

                        String statusColor;
                        switch (status.toUpperCase()) {
                            case "SUCCESS":
                                statusColor = "§a";
                                break;
                            case "PENDING":
                                statusColor = "§e";
                                break;
                            default:
                                statusColor = "§c";
                                break;
                        }

                        player.sendMessage("§7" + count + ". §f[" + type + "] §e" + amount + " VND §7→ §b"
                                + crystal + " Crystal " + statusColor + status + " §8" + date);
                    }

                    if (count == 0) {
                        player.sendMessage(prefix + "§7Bạn chưa có giao dịch nào.");
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage(prefix + "§cLỗi khi tải lịch sử nạp.");
            }
        });
    }

    private void handleAdHistory(Player player, String[] args) {
        String prefix = plugin.getConfigManager().getMessage("prefix");

        if (!player.hasPermission("napthe.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        int page = 1;
        String targetName = "all";

        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                targetName = args[1];
            }
        } else if (args.length >= 3) {
            targetName = args[1];
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        if (page < 1) page = 1;
        final int finalPage = page;
        final String finalTargetName = targetName;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int limit = 10;
            int offset = (finalPage - 1) * limit;

            Connection conn = plugin.getSQLiteManager().getConnection();
            try {
                String query;
                PreparedStatement ps;

                if (finalTargetName.equalsIgnoreCase("all")) {
                    query = "SELECT t.type, t.amount_vnd, t.amount_crystal, t.status, t.timestamp, u.name FROM transactions t LEFT JOIN users u ON t.uuid = u.uuid ORDER BY t.timestamp DESC LIMIT ? OFFSET ?";
                    ps = conn.prepareStatement(query);
                    ps.setInt(1, limit);
                    ps.setInt(2, offset);
                    player.sendMessage(prefix + "§e--- Lịch sử nạp (Tất cả) - Trang " + finalPage + " ---");
                } else {
                    query = "SELECT t.type, t.amount_vnd, t.amount_crystal, t.status, t.timestamp, u.name FROM transactions t JOIN users u ON t.uuid = u.uuid WHERE u.name = ? COLLATE NOCASE ORDER BY t.timestamp DESC LIMIT ? OFFSET ?";
                    ps = conn.prepareStatement(query);
                    ps.setString(1, finalTargetName);
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                    player.sendMessage(prefix + "§e--- Lịch sử nạp của " + finalTargetName + " - Trang " + finalPage + " ---");
                }

                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        String type = rs.getString("type");
                        int amount = rs.getInt("amount_vnd");
                        double crystal = rs.getDouble("amount_crystal");
                        String status = rs.getString("status");
                        long ts = rs.getLong("timestamp");
                        String pName = rs.getString("name");
                        if (pName == null) pName = "Unknown";
                        String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(ts));

                        String statusColor;
                        switch (status.toUpperCase()) {
                            case "SUCCESS":
                                statusColor = "§a";
                                break;
                            case "PENDING":
                                statusColor = "§e";
                                break;
                            default:
                                statusColor = "§c";
                                break;
                        }

                        player.sendMessage("§7" + ((finalPage - 1) * limit + count) + ". §a" + pName + " §f[" + type + "] §e" + amount + " VND §7→ §b"
                                + crystal + " Crystal " + statusColor + status + " §8" + date);
                    }

                    if (count == 0) {
                        player.sendMessage(prefix + "§7Không có giao dịch nào ở trang này.");
                    } else {
                        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent("§e« Trang trước");
                        if (finalPage > 1) {
                            msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/napthe adhistory " + finalTargetName + " " + (finalPage - 1)));
                            msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("Click để xem trang " + (finalPage - 1))));
                        } else {
                            msg.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                        }

                        net.md_5.bungee.api.chat.TextComponent next = new net.md_5.bungee.api.chat.TextComponent(" §7| §eTrang sau »");
                        if (count == limit) {
                            next.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/napthe adhistory " + finalTargetName + " " + (finalPage + 1)));
                            next.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("Click để xem trang " + (finalPage + 1))));
                        } else {
                            next.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                        }

                        msg.addExtra(next);
                        player.spigot().sendMessage(msg);
                    }
                }

                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage(prefix + "§cLỗi khi tải lịch sử nạp.");
            }
        });
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("napthe.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        plugin.getConfigManager().loadConfigs();
        player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§aĐã tải lại cấu hình thành công!");
    }

    private int parseAmount(String arg) {
        arg = arg.toLowerCase().replace("k", "000").replace("m", "000000");
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void generateQR(Player player, int amount) {
        String requestId = "NAP_" + player.getName() + "_" + amount;

        try {
            // Đọc cấu hình SePay từ config.yml
            String merchantId = plugin.getConfig().getString("sepay.merchant_id", "");
            String bank = plugin.getConfig().getString("sepay.bank_code", "MB");
            String account = plugin.getConfig().getString("sepay.account_number", "000000000");
            String accountName = plugin.getConfig().getString("sepay.account_name", "");
            String template = plugin.getConfig().getString("sepay.qr_template", "compact");

            String qrUrl;
            if (merchantId != null && !merchantId.isEmpty() && !merchantId.equals("your_merchant_id")) {
                qrUrl = "https://qr.sepay.vn/img?api=" + merchantId 
                        + "&amount=" + amount 
                        + "&des=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8.toString())
                        + "&template=" + template;
            } else {
                qrUrl = "https://qr.sepay.vn/img?acc=" + account 
                        + "&bank=" + bank 
                        + "&amount=" + amount 
                        + "&des=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8.toString())
                        + "&template=" + template 
                        + "&accountName=" + URLEncoder.encode(accountName, StandardCharsets.UTF_8.toString());
            }

            MapView mapView = Bukkit.createMap(player.getWorld());
            mapView.getRenderers().clear();
            mapView.addRenderer(new QRMapRenderer(qrUrl));

            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            meta.setMapView(mapView);

            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "qr_id");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, requestId);

            String currencySymbol = plugin.getConfig().getString("currency.primary_symbol", "💎");
            String currencyName = plugin.getConfig().getString("currency.primary_name", "Crystal");
            meta.setDisplayName(org.bukkit.ChatColor.GREEN + "Quét mã QR để nạp " + amount + " VND " + currencySymbol);
            mapItem.setItemMeta(meta);

            player.getInventory().addItem(mapItem);

            // Xóa QR sau 30 phút
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    boolean removed = false;
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        ItemStack item = player.getInventory().getItem(i);
                        if (item != null && item.hasItemMeta()) {
                            if (requestId.equals(item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                                player.getInventory().setItem(i, null);
                                removed = true;
                            }
                        }
                    }
                    if (removed) {
                        player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§cMã QR nạp thẻ của bạn đã hết hạn (30 phút)!");
                    }
                }
            }, 30 * 60 * 20L);

            // Add pending transaction to DB
            plugin.getSQLiteManager().addTransaction(requestId, player.getUniqueId(), "QR", amount, 0, "PENDING");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
