package com.sergicraftero.escapes.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu. All clicks inside are cancelled; buttons get a handler. Works the same on Java and on Bedrock
 * (Geyser shows chest menus natively).
 */
public class Menu implements InventoryHolder {

    public record Click(Player player, int slot, ClickType type, Menu menu) {
        public boolean right() {
            return type.isRightClick();
        }

        public boolean shift() {
            return type.isShiftClick();
        }
    }

    private final Inventory inventory;
    private final Map<Integer, Consumer<Click>> handlers = new HashMap<>();
    private Runnable onClose;
    private boolean closing;

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    public void set(int slot, ItemStack item, Consumer<Click> handler) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, handler);
        }
    }

    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void clear() {
        inventory.clear();
        handlers.clear();
    }

    public void fill(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public void onClose(Runnable r) {
        this.onClose = r;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    /** Closes for the player without firing the close handler (used when switching menus). */
    public void closeSilently(Player p) {
        closing = true;
        p.closeInventory();
        closing = false;
    }

    void click(Click click) {
        Consumer<Click> h = handlers.get(click.slot());
        if (h != null) {
            h.accept(click);
        }
    }

    void closed() {
        if (!closing && onClose != null) {
            onClose.run();
        }
    }
}
