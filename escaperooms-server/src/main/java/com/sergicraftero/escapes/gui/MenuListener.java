package com.sergicraftero.escapes.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Routes clicks to {@link Menu}s and blocks every way of moving items in or out of them. */
public final class MenuListener implements Listener {

    /** Per-player click cooldown: Bedrock and fast double clicks fire twice. */
    private final Map<UUID, Long> lastClick = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() != top) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < 120) {
            return;
        }
        lastClick.put(p.getUniqueId(), now);
        menu.click(new Menu.Click(p, e.getRawSlot(), e.getClick(), menu));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu) {
            menu.closed();
        }
        lastClick.remove(e.getPlayer().getUniqueId());
    }
}
