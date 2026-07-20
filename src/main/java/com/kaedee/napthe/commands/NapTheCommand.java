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
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "card":
                if (checkPlayer(sender)) handleCard((Player) sender, args);
                break;
            case "qr":
                if (checkPlayer(sender)) handleQR((Player) sender, args);
                break;
            case "info":
                if (checkPlayer(sender)) handleInfo((Player) sender);
                break;
            case "history":
                if (checkPlayer(sender)) handleHistory((Player) sender);
                break;
            case "adhistory":
                if (checkPlayer(sender)) handleAdHistory((Player) sender, args);
                break;
            case "reload":
                if (checkPlayer(sender)) handleReload((Player) sender);
                break;
            case "take":
                handleTake(sender, args);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "balance":
                handleBalance(sender, args);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }
    
    private boolean checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        String prefix = plugin.getConfigManager().getMessage("prefix");
        sender.sendMessage(prefix + "§eCác lệnh khả dụng:");
        sender.sendMessage("  §a/napthe info §7- Xem số dư Crystal của bạn");
        sender.sendMessage("  §a/napthe card <amount> <telco> <code> <serial> §7- Nạp thẻ cào");
        sender.sendMessage("  §a/napthe qr <amount> §7- Tạo mã QR chuyển khoản");
        sender.sendMessage("  §a/napthe history §7- Xem lịch sử nạp");
        if (sender.hasPermission("napthe.admin")) {
            sender.sendMessage("  §a/napthe adhistory [player|all] [page] §7- Xem lịch sử nạp");
            sender.sendMessage("  §a/napthe reload §7- Tải lại cấu hình");
        }
        if (sender.hasPermission("napthe.admin.give")) {
            sender.sendMessage("  §a/napthe give <player> <amount> §7- Cộng Crystal");
        }
        if (sender.hasPermission("napthe.admin.take")) {
            sender.sendMessage("  §a/napthe take <player> <amount> §7- Trừ Crystal");
        }
        if (sender.hasPermission("napthe.admin.balance")) {
            sender.sendMessage("  §a/napthe balance <player> §7- Xem số dư Crystal");
        }
    }

    private void handleInfo(Player player) {
        String prefix = plugin.getConfigManager().getMessage("prefix");
        String crystalName = plugin.getConfigManager().getConfig().getString("currency.primary_name", "Crystal");
        String crystalSymbol = plugin.getConfigManager().getConfig().getString("currency.primary_symbol", "💎");
        double balance = plugin.getSQLiteManager().getCrystal(player.getUniqueId());
        java.text.DecimalFormat rawFmt = new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        player.sendMessage(prefix + "§eSố dư của bạn: §b" + rawFmt.format(balance) + " §f" + crystalSymbol + " " + crystalName);
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
        String randomSuffix = String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));
        String requestId = "NAP_" + player.getName() + "_" + amount + "_" + randomSuffix;

        try {
            // Đọc cấu hình SePay từ config.yml
            String merchantId = plugin.getConfig().getString("sepay.merchant_id", "");
            String secretKey = plugin.getConfig().getString("sepay.secret_key", "");
            String bank = plugin.getConfig().getString("sepay.bank_code", "MB");
            String account = plugin.getConfig().getString("sepay.account_number", "000000000");
            String accountName = plugin.getConfig().getString("sepay.account_name", "");
            String template = plugin.getConfig().getString("sepay.qr_template", "compact");
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "qr_id");

            String qrUrlFallback = "https://qr.sepay.vn/img?acc=" + account 
                    + "&bank=" + bank 
                    + "&amount=" + amount 
                    + "&des=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8.toString())
                    + "&template=" + template 
                    + "&accountName=" + URLEncoder.encode(accountName, StandardCharsets.UTF_8.toString());

            player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§eĐang khởi tạo mã QR, vui lòng đợi...");

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String checkoutUrl = null;
                String finalQrUrl = qrUrlFallback;
                
                if (merchantId != null && !merchantId.isEmpty() && !merchantId.equals("your_merchant_id") 
                    && secretKey != null && !secretKey.isEmpty() && !secretKey.equals("your_sepay_secret_key")) {
                    
                    checkoutUrl = com.kaedee.napthe.utils.SePayUtils.createCheckoutUrl(
                            merchantId, 
                            secretKey, 
                            amount, 
                            requestId, 
                            "Nap the " + player.getName(), 
                            player.getName(),
                            "", // successUrl
                            "", // errorUrl
                            "", // cancelUrl
                            plugin.getLogger()
                    );
                    
                    if (checkoutUrl != null) {
                        String extractedQrUrl = com.kaedee.napthe.utils.SePayUtils.extractQrImageUrl(checkoutUrl);
                        if (extractedQrUrl != null) {
                            finalQrUrl = extractedQrUrl;
                            plugin.getLogger().info("[SePay] Lấy thành công ảnh QR từ trang thanh toán: " + finalQrUrl);
                        }

                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("order_id=([^&]+)").matcher(checkoutUrl);
                        if (m.find()) {
                            String sepayOrderId = m.group(1);
                            plugin.getLogger().info("[SePay] Bắt đầu theo dõi đơn hàng: " + sepayOrderId);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                int attempts = 0;
                                @Override
                                public void run() {
                                    attempts++;
                                    if (attempts > 120) { // Timeout sau 10 phút
                                        this.cancel();
                                        return;
                                    }
                                    if (com.kaedee.napthe.utils.SePayUtils.checkOrderStatus(merchantId, secretKey, requestId, plugin.getLogger())) {
                                        this.cancel();
                                        plugin.getLogger().info("[SePay] Đơn hàng " + requestId + " đã thanh toán (via Polling).");
                                        
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            java.util.UUID uuid = plugin.getSQLiteManager().getUuidByTxId(requestId);
                                            if (uuid != null) {
                                                String status = plugin.getSQLiteManager().getTransactionStatus(requestId);
                                                if ("PENDING".equalsIgnoreCase(status)) {
                                                    double crystalRate = plugin.getConfig().getDouble("rates.qr_rate", 1.0);
                                                    double crystals = (amount / 10000.0) * crystalRate;
                                                    plugin.getSQLiteManager().updateTransactionStatus(requestId, "SUCCESS");
                                                    plugin.getSQLiteManager().addCrystalAndTotalDonated(uuid, crystals, amount);
                                                    plugin.processRewardsAndNotify(uuid, amount, crystals, true);
                                                }
                                            }
                                        });
                                    }
                                }
                            }.runTaskTimerAsynchronously(plugin, 100L, 100L); // 5s interval
                        }
                    }
                }

                // Tải ảnh QR trên thread phụ để tránh giật lag server
                java.awt.image.BufferedImage img = null;
                try {
                    java.net.URL url = new java.net.URL(finalQrUrl);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    img = javax.imageio.ImageIO.read(connection.getInputStream());
                } catch (Exception e) {
                    plugin.getLogger().warning("[SePay] Không thể tải ảnh QR: " + e.getMessage());
                }
                
                final String finalCheckoutUrl = checkoutUrl;
                final java.awt.image.BufferedImage finalImg = img;
                
                // Quay lại Main Thread để xử lý các thao tác của Bukkit (tạo Map, thêm Item)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    
                    if (finalCheckoutUrl != null) {
                        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                                plugin.getConfigManager().getMessage("prefix") + "§a§l[BẤM VÀO ĐÂY ĐỂ MỞ TRANG THANH TOÁN SEPAY]"
                        );
                        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, finalCheckoutUrl));
                        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("§eClick để mở link thanh toán")));
                        
                        player.spigot().sendMessage(msg);
                        player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§7Hoặc bạn có thể sử dụng bản đồ QR trên tay để quét.");
                    } else if (merchantId != null && !merchantId.equals("your_merchant_id")) {
                        player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§cLỗi khởi tạo liên kết thanh toán. Bạn vẫn có thể dùng QR Code trên tay!");
                    }

                    MapView mapView = Bukkit.createMap(player.getWorld());
                    mapView.getRenderers().clear();
                    if (finalImg != null) {
                        mapView.addRenderer(new QRMapRenderer(finalImg));
                    } else {
                        mapView.addRenderer(new QRMapRenderer(qrUrlFallback));
                    }

                    ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                    MapMeta meta = (MapMeta) mapItem.getItemMeta();
                    meta.setMapView(mapView);

                    meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, requestId);

                    String currencySymbol = plugin.getConfig().getString("currency.primary_symbol", "💎");
                    String currencyName = plugin.getConfig().getString("currency.primary_name", "Crystal");
                    meta.setDisplayName(org.bukkit.ChatColor.GREEN + "Quét mã QR để nạp " + amount + " VND " + currencySymbol);
                    mapItem.setItemMeta(meta);

                    player.getInventory().addItem(mapItem);
                });
            });

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

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("napthe.admin.give")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cSử dụng: /napthe give <player> <amount>");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        try {
            double amount = Double.parseDouble(args[2]);
            plugin.getSQLiteManager().giveCrystal(target.getUniqueId(), amount);
            sender.sendMessage("§aĐã cộng " + amount + " Crystal cho " + target.getName() + ".");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSố lượng không hợp lệ.");
        }
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("napthe.admin.take")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cSử dụng: /napthe take <player> <amount> [commands...]");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§cSố lượng phải lớn hơn 0.");
                return;
            }
            // ✅ Kiểm tra số dư trước khi trừ — ngăn crystal xuống âm
            if (!plugin.getSQLiteManager().hasSufficientCrystal(target.getUniqueId(), amount)) {
                double current = plugin.getSQLiteManager().getCrystal(target.getUniqueId());
                java.text.DecimalFormat rawFmt = new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
                
                // Gửi thông báo cho console
                sender.sendMessage("§cKhông đủ Crystal! " + target.getName() + " chỉ có §e" + rawFmt.format(current) + " §cCrystal.");
                
                // Gửi trực tiếp cho người chơi nếu đang online
                if (target.isOnline() && target.getPlayer() != null) {
                    target.getPlayer().sendMessage("§cBạn không có đủ Crystal! Cần ít nhất §b" + rawFmt.format(amount) + " Crystal§c. Bạn hiện có §e" + rawFmt.format(current) + " Crystal§c.");
                }
                return;
            }
            plugin.getSQLiteManager().removeCrystal(target.getUniqueId(), amount);
            
            // Gửi thông báo cho console/người gõ lệnh
            sender.sendMessage("§aĐã trừ " + amount + " Crystal của " + target.getName() + ".");
            // Gửi thông báo cho người chơi nếu họ đang online
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage("§aĐã thanh toán thành công §e" + amount + " Crystal§a!");
            }

            // ✅ Chạy lệnh đính kèm nếu giao dịch thành công (Hỗ trợ cấu hình UltimateShop)
            if (args.length > 3) {
                StringBuilder commandBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    commandBuilder.append(args[i]).append(" ");
                }
                String cmdToRun = commandBuilder.toString().trim();
                // Hỗ trợ nhiều lệnh bằng dấu ;
                String[] commands = cmdToRun.split(";");
                for (String cmd : commands) {
                    if (!cmd.trim().isEmpty()) {
                        // Chạy lệnh từ Console
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
                        });
                    }
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSố lượng không hợp lệ.");
        }
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("napthe.admin.balance")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cSử dụng: /napthe balance <player>");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double balance = plugin.getSQLiteManager().getCrystal(target.getUniqueId());
        java.text.DecimalFormat rawFmt = new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        sender.sendMessage("§aSố dư Crystal của " + target.getName() + " là: §e" + rawFmt.format(balance));
    }
}
