package com.kaedee.napthe.listeners;

import com.kaedee.napthe.NapThe;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class QRListener implements Listener {

    private final NapThe plugin;
    private final NamespacedKey qrKey;

    public QRListener(NapThe plugin) {
        this.plugin = plugin;
        this.qrKey = new NamespacedKey(plugin, "qr_id");
    }

    private boolean isQRMap(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(qrKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isQRMap(item)) {
            event.getItemDrop().remove();
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("prefix") + "§cBạn đã vứt mã QR, mã đã bị xóa!");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isQRMap(current) || isQRMap(cursor)) {
            // Ngăn cất vào rương/kho, ép xóa
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) {
                event.setCancelled(true);
                
                if (isQRMap(current)) {
                    event.setCurrentItem(null);
                }
                if (isQRMap(cursor)) {
                    event.getView().setCursor(null);
                }
                
                if (event.getWhoClicked() instanceof Player) {
                    event.getWhoClicked().sendMessage(plugin.getConfigManager().getMessage("prefix") + "§cKhông thể cất mã QR vào kho, mã đã tự động bị xóa!");
                }
            }
        }
    }
}
