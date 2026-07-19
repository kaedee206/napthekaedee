package com.kaedee.napthe;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final NapThe plugin;
    private FileConfiguration config;
    private FileConfiguration rewardConfig;

    public ConfigManager(NapThe plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        File rewardFile = new File(plugin.getDataFolder(), "reward.yml");
        if (!rewardFile.exists()) {
            plugin.saveResource("reward.yml", false);
        }
        this.rewardConfig = YamlConfiguration.loadConfiguration(rewardFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getRewardConfig() {
        return rewardConfig;
    }

    public String getMessage(String path) {
        String msg = config.getString("messages." + path, "");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
