package com.kaedee.napthe;

import com.kaedee.napthe.commands.NapTheCommand;
import com.kaedee.napthe.commands.NapTheTabCompleter;
import com.kaedee.napthe.commands.TopNapCommand;
import com.kaedee.napthe.commands.NapInfoCommand;
import com.kaedee.napthe.discord.DiscordManager;
import com.kaedee.napthe.hooks.NapThePlaceholders;
import com.kaedee.napthe.database.SQLiteManager;
import com.kaedee.napthe.http.HttpServerManager;
import com.kaedee.napthe.http.TsrManager;
import com.kaedee.napthe.utils.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Sound;

import java.util.UUID;
import java.util.logging.Level;

public class NapThe extends JavaPlugin {

    private ConfigManager configManager;
    private SQLiteManager sqLiteManager;
    private HttpServerManager httpServerManager;
    private TsrManager tsrManager;
    private VaultManager vaultManager;
    private DiscordManager discordManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        sqLiteManager = new SQLiteManager(this);
        sqLiteManager.connect();

        vaultManager = new VaultManager();
        if (!vaultManager.setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault economy not found! Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tsrManager = new TsrManager(this, configManager);

        httpServerManager = new HttpServerManager(this, configManager);
        httpServerManager.startServer();

        discordManager = new DiscordManager(this);
        discordManager.start();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new com.kaedee.napthe.listeners.QRListener(this), this);

        getCommand("napthe").setExecutor(new NapTheCommand(this));
        getCommand("napthe").setTabCompleter(new NapTheTabCompleter());
        getCommand("topnap").setExecutor(new TopNapCommand(this));
        getCommand("napinfo").setExecutor(new NapInfoCommand(this));
        
        // Đăng ký PlaceholderAPI expansion nếu có
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NapThePlaceholders(this).register();
            getLogger().info("PlaceholderAPI hooked successfully!");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }

        getLogger().info("NapThe plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (httpServerManager != null) {
            httpServerManager.stopServer();
        }
        if (discordManager != null) {
            discordManager.shutdown();
        }
        if (sqLiteManager != null) {
            sqLiteManager.close();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SQLiteManager getSQLiteManager() {
        return sqLiteManager;
    }

    public TsrManager getTsrManager() {
        return tsrManager;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public void debug(String message) {
        if (configManager != null && configManager.getConfig().getBoolean("debug_mode", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void debug(Exception e) {
        if (configManager != null && configManager.getConfig().getBoolean("debug_mode", false)) {
            e.printStackTrace();
        }
    }

    public void processRewardsAndNotify(UUID uuid, double vndAmount, double crystals, boolean success) {
        Bukkit.getScheduler().runTask(this, () -> {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);

            // Remove any pending QR maps from this player's inventory
            if (player != null) {
                removeQrMaps(player);
            }

            // Quy đổi Crystal -> Koin đã bị loại bỏ. Người chơi sẽ dùng UltimateShop để đổi Koin.
            
            if (player != null) {
                if (success) {
                    String msg = configManager.getMessage("charged_success")
                            .replace("%vnd%", String.valueOf((int)vndAmount))
                            .replace("%crystal%", String.valueOf(crystals));
                    Bukkit.getPlayer(uuid).sendMessage(configManager.getMessage("prefix") + msg);
                    
                    // Thêm hiệu ứng và Broadcast
                    boolean enableBroadcast = configManager.getConfig().getBoolean("messages.enable_broadcast", true);
                    double challengeThreshold = configManager.getConfig().getDouble("effects.challenge_threshold", 500000.0);
                    
                    if (vndAmount >= challengeThreshold) {
                        // Hiệu ứng mốc cao
                        String sound = configManager.getConfig().getString("effects.challenge_complete_sound", "UI_TOAST_CHALLENGE_COMPLETE");
                        try {
                            Bukkit.getPlayer(uuid).playSound(Bukkit.getPlayer(uuid).getLocation(), Sound.valueOf(sound), 1.0f, 1.0f);
                        } catch (Exception ignored) {}

                        if (enableBroadcast) {
                            String bcMsg = configManager.getMessage("broadcast_challenge")
                                    .replace("%player%", name)
                                    .replace("%vnd%", String.valueOf((int)vndAmount));
                            Bukkit.broadcastMessage(configManager.getMessage("prefix") + bcMsg);
                        }

                        // Gửi Discord embed mốc lớn
                        if (discordManager != null) {
                            discordManager.sendTopupEmbed(name, vndAmount, crystals, "QR/CARD", true);
                        }
                    } else {
                        // Hiệu ứng nạp bình thường
                        String sound = configManager.getConfig().getString("effects.level_up_sound", "ENTITY_PLAYER_LEVELUP");
                        try {
                            Bukkit.getPlayer(uuid).playSound(Bukkit.getPlayer(uuid).getLocation(), Sound.valueOf(sound), 1.0f, 1.0f);
                        } catch (Exception ignored) {}

                        if (enableBroadcast) {
                            String bcMsg = configManager.getMessage("broadcast_topup")
                                    .replace("%player%", name)
                                    .replace("%vnd%", String.valueOf((int)vndAmount));
                            Bukkit.broadcastMessage(configManager.getMessage("prefix") + bcMsg);
                        }

                        // Gửi Discord embed nạp thường
                        if (discordManager != null) {
                            discordManager.sendTopupEmbed(name, vndAmount, crystals, "QR/CARD", false);
                        }
                    }
                } else {
                    String msg = configManager.getMessage("charged_wrong_value")
                            .replace("%vnd%", String.valueOf((int)vndAmount))
                            .replace("%crystal%", String.valueOf(crystals));
                    Bukkit.getPlayer(uuid).sendMessage(configManager.getMessage("prefix") + msg);
                }
            }

            // Check Milestones
            double totalDonated = sqLiteManager.getTotalDonated(uuid);
            ConfigurationSection milestones = configManager.getRewardConfig().getConfigurationSection("milestones");
            if (milestones != null) {
                for (String key : milestones.getKeys(false)) {
                    double amountNeeded = milestones.getDouble(key + ".amount");
                    // Very simple check: if we just crossed the milestone
                    if (totalDonated >= amountNeeded && (totalDonated - vndAmount) < amountNeeded) {
                        for (String cmd : milestones.getStringList(key + ".commands")) {
                            cmd = cmd.replace("%player%", name).replace("%rank%", key);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        }
                    }
                }
            }
        });
    }

    public void removeQrMaps(org.bukkit.entity.Player player) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this, "qr_id");
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }
}
