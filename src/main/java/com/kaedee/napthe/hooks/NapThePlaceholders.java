package com.kaedee.napthe.hooks;

import com.kaedee.napthe.NapThe;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;

/**
 * PlaceholderAPI Expansion cho NapThe.
 *
 * Các placeholder có sẵn:
 *   %napthe_crystal%           - Số Crystal hiện tại
 *   %napthe_crystal_formatted% - Số Crystal có định dạng (theo config)
 *   %napthe_crystal_name%      - Tên đơn vị Crystal (theo config)
 *   %napthe_crystal_symbol%    - Biểu tượng Crystal (theo config)
 *   %napthe_total_donated%     - Tổng VND đã nạp
 *   %napthe_total_formatted%   - Tổng VND có định dạng
 *   %napthe_koin_name%         - Tên đơn vị Koin (theo config)
 *   %napthe_koin_symbol%       - Biểu tượng Koin (theo config)
 *   %napthe_rank%              - Rank hiện tại dựa trên tổng nạp
 *   %napthe_transactions%      - Số lượng giao dịch đã thực hiện
 */
public class NapThePlaceholders extends PlaceholderExpansion {

    private final NapThe plugin;

    public NapThePlaceholders(NapThe plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "napthe";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Kaedee";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // Không bị unregister khi PlaceholderAPI reload
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String numberFormat = plugin.getConfigManager().getConfig()
                .getString("currency.number_format", "#,###.##");
        DecimalFormat df = new DecimalFormat(numberFormat);

        switch (params.toLowerCase()) {
            case "crystal": {
                double crystal = queryCrystal(player);
                return String.valueOf((int) crystal);
            }
            case "crystal_formatted": {
                double crystal = queryCrystal(player);
                return df.format(crystal);
            }
            case "crystal_name": {
                return plugin.getConfigManager().getConfig()
                        .getString("currency.primary_name", "Crystal");
            }
            case "crystal_symbol": {
                return plugin.getConfigManager().getConfig()
                        .getString("currency.primary_symbol", "💎");
            }
            case "total_donated": {
                double total = queryTotalDonated(player);
                return String.valueOf((int) total);
            }
            case "total_formatted": {
                double total = queryTotalDonated(player);
                return df.format(total);
            }

            case "rank": {
                return queryRank(player);
            }
            case "transactions": {
                return String.valueOf(queryTransactionCount(player));
            }
            default:
                return null;
        }
    }

    /**
     * Lấy số Crystal hiện tại của player từ SQLite.
     * Lưu ý: Connection là shared, KHÔNG đóng connection — chỉ đóng PreparedStatement và ResultSet.
     */
    private double queryCrystal(OfflinePlayer player) {
        try {
            Connection conn = plugin.getSQLiteManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT current_crystal FROM users WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            double val = 0;
            if (rs.next()) {
                val = rs.getDouble("current_crystal");
            }
            rs.close();
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Lấy tổng VND đã nạp của player từ SQLite.
     */
    private double queryTotalDonated(OfflinePlayer player) {
        try {
            Connection conn = plugin.getSQLiteManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT total_donated FROM users WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            double val = 0;
            if (rs.next()) {
                val = rs.getDouble("total_donated");
            }
            rs.close();
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Tính rank hiện tại dựa trên tổng nạp và config reward.yml milestones.
     */
    private String queryRank(OfflinePlayer player) {
        double total = queryTotalDonated(player);
        String rank = "Mặc định";

        var milestones = plugin.getConfigManager().getRewardConfig()
                .getConfigurationSection("milestones");
        if (milestones != null) {
            double highestAmount = 0;
            for (String key : milestones.getKeys(false)) {
                double needed = milestones.getDouble(key + ".amount", 0);
                if (total >= needed && needed > highestAmount) {
                    highestAmount = needed;
                    rank = key;
                }
            }
        }
        return rank;
    }

    /**
     * Đếm số lượng giao dịch của player.
     */
    private int queryTransactionCount(OfflinePlayer player) {
        try {
            Connection conn = plugin.getSQLiteManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) as cnt FROM transactions WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            int val = 0;
            if (rs.next()) {
                val = rs.getInt("cnt");
            }
            rs.close();
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
