package com.kaedee.napthe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final NapThe plugin;

    public PlayerJoinListener(NapThe plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        String ip = event.getPlayer().getAddress() != null ? event.getPlayer().getAddress().getAddress().getHostAddress() : "unknown";
        
        plugin.getSQLiteManager().updatePlayerIPAndName(event.getPlayer().getUniqueId(), name, ip);
    }
}
