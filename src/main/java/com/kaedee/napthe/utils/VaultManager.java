package com.kaedee.napthe.utils;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultManager {

    private Economy econ = null;

    public boolean setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Cộng tiền Vault (Koin) cho người chơi.
     * Hỗ trợ cả online và offline player.
     */
    public void addMoney(OfflinePlayer player, double amount) {
        if (econ != null && player != null) {
            econ.depositPlayer(player, amount);
        }
    }

    public Economy getEconomy() {
        return econ;
    }
}
