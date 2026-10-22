package com.sergicraftero.escapes.it;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock;

import java.util.Objects;

/**
 * MockBukkit's ItemStack#isSimilar only compares the material, so two different room items made of paper
 * would merge into one stack. A real server compares the whole meta; this inventory does the same.
 */
public class StrictPlayerInventory extends PlayerInventoryMock {

    private static final int STORAGE = 36;

    public StrictPlayerInventory(HumanEntity holder) {
        super(holder);
    }

    static boolean similar(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getType() != b.getType()) {
            return false;
        }
        ItemMeta ma = a.hasItemMeta() ? a.getItemMeta() : null;
        ItemMeta mb = b.hasItemMeta() ? b.getItemMeta() : null;
        if (ma == null || mb == null) {
            return ma == mb;
        }
        return Objects.equals(ma.displayName(), mb.displayName())
                && Objects.equals(ma.getPersistentDataContainer(), mb.getPersistentDataContainer());
    }

    @Override
    public ItemStack addItem(ItemStack item) {
        ItemStack left = item.clone();
        int max = left.getMaxStackSize();
        for (int i = 0; i < STORAGE && left.getAmount() > 0; i++) {
            ItemStack slot = getItem(i);
            if (similar(slot, left) && slot.getAmount() < max) {
                int move = Math.min(left.getAmount(), max - slot.getAmount());
                slot.setAmount(slot.getAmount() + move);
                setItem(i, slot);
                left.setAmount(left.getAmount() - move);
            }
        }
        for (int i = 0; i < STORAGE && left.getAmount() > 0; i++) {
            ItemStack slot = getItem(i);
            if (slot == null || slot.getType().isAir()) {
                ItemStack put = left.clone();
                put.setAmount(Math.min(left.getAmount(), max));
                setItem(i, put);
                left.setAmount(left.getAmount() - put.getAmount());
            }
        }
        return left.getAmount() > 0 ? left : null;
    }
}
