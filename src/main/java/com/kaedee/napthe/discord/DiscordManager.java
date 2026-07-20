package com.kaedee.napthe.discord;

import com.kaedee.napthe.NapThe;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.Color;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Quản lý Discord Bot và gửi embed thông báo nạp thẻ.
 *
 * Cấu hình trong config.yml:
 *   discord.enabled        - Bật/tắt
 *   discord.bot_token      - Token của bot
 *   discord.channel_id     - ID channel nhận thông báo
 *   discord.embed_color    - Màu embed hex (không có #), mặc định 00FF88
 *   discord.embed_title    - Tiêu đề embed thông thường
 *   discord.show_avatar    - Hiển thị avatar Minecraft player
 *   discord.notify_challenge  - Gửi embed riêng khi nạp mốc lớn
 *   discord.challenge_title   - Tiêu đề embed challenge
 *   discord.challenge_color   - Màu embed challenge hex
 */
public class DiscordManager {

    private final NapThe plugin;
    private JDA jda;
    private boolean enabled = false;

    public DiscordManager(NapThe plugin) {
        this.plugin = plugin;
    }

    /**
     * Khởi động JDA bot. Gọi trong onEnable sau khi config đã load.
     */
    public void start() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        enabled = config.getBoolean("discord.enabled", false);
        if (!enabled) {
            plugin.getLogger().info("[Discord] Tính năng Discord bot bị tắt (discord.enabled: false).");
            return;
        }

        String token = config.getString("discord.bot_token", "");
        if (token.isBlank() || token.equals("your_bot_token_here")) {
            plugin.getLogger().warning("[Discord] Chưa cấu hình bot_token! Discord bot sẽ không khởi động.");
            enabled = false;
            return;
        }

        String channelId = config.getString("discord.channel_id", "");
        if (channelId.isBlank() || channelId.equals("your_channel_id_here")) {
            plugin.getLogger().warning("[Discord] Chưa cấu hình channel_id! Discord bot sẽ không khởi động.");
            enabled = false;
            return;
        }

        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                    .build()
                    .awaitReady();
            plugin.getLogger().info("[Discord] Bot đã kết nối thành công: " + jda.getSelfUser().getAsTag());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Discord] Không thể khởi động bot: " + e.getMessage(), e);
            enabled = false;
        }
    }

    /**
     * Tắt JDA bot. Gọi trong onDisable.
     */
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    /**
     * Gửi embed thông báo nạp thẻ thành công lên Discord.
     *
     * @param playerName  Tên người chơi
     * @param vndAmount   Số tiền VND đã nạp
     * @param crystal     Số Crystal nhận được
     * @param type        Loại giao dịch ("QR" hoặc "CARD")
     * @param isChallenge True nếu vndAmount >= challenge_threshold
     */
    public void sendTopupEmbed(String playerName, double vndAmount, double crystal,
                               String type, boolean isChallenge) {
        if (!enabled || jda == null) return;

        FileConfiguration config = plugin.getConfigManager().getConfig();
        String channelId = config.getString("discord.channel_id", "");

        // Chạy trên thread async để không block main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel == null) {
                    plugin.getLogger().warning("[Discord] Không tìm thấy channel ID: " + channelId);
                    return;
                }

                String crystalName   = config.getString("currency.primary_name", "Crystal");
                String crystalSymbol = config.getString("currency.primary_symbol", "💎");
                boolean showAvatar   = config.getBoolean("discord.show_avatar", true);

                // Chọn màu và tiêu đề theo loại
                String hexColor, title;
                if (isChallenge && config.getBoolean("discord.notify_challenge", true)) {
                    hexColor = config.getString("discord.challenge_color", "FFD700");
                    title    = config.getString("discord.challenge_title", "🎉 Mốc nạp thẻ lớn!");
                } else {
                    hexColor = config.getString("discord.embed_color", "00FF88");
                    title    = config.getString("discord.embed_title", "💎 Nạp thẻ thành công!");
                }

                Color color;
                try {
                    color = Color.decode("#" + hexColor);
                } catch (NumberFormatException e) {
                    color = new Color(0x00FF88);
                }

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(title)
                        .setColor(color)
                        .addField("👤 Người chơi", "**" + playerName + "**", true)
                        .addField("💵 Số tiền", String.format("%,.0f VND", vndAmount), true)
                        .addField(crystalSymbol + " " + crystalName, String.format("%,.0f " + crystalName, crystal), true)
                        .addField("📋 Loại", type, true)
                        .setFooter("NapThe Plugin", null)
                        .setTimestamp(Instant.now());

                // Thêm avatar Minecraft (sử dụng Crafatar)
                if (showAvatar) {
                    String avatarUrl = "https://crafatar.com/avatars/" + playerName + "?size=64&overlay";
                    embed.setThumbnail(avatarUrl);
                }

                channel.sendMessageEmbeds(embed.build()).queue(
                        success -> plugin.debug("[Discord] Đã gửi embed cho " + playerName),
                        error   -> plugin.getLogger().warning("[Discord] Gửi embed thất bại: " + error.getMessage())
                );

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Discord] Lỗi khi gửi embed: " + e.getMessage(), e);
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }
}
