package com.sergicraftero.escaperoom.room;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom room item definition. Items are tagged in their PDC with "roomId:itemId" so puzzles
 * can recognise them regardless of display name.
 */
public record ItemSpec(String roomId, String id, Material material, String name, List<String> lore,
                       boolean glow, Color color, String bookTitle, String bookAuthor, List<String> pages) {

    public static ItemSpec load(String roomId, String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "PAPER"));
        if (material == null) {
            material = Material.PAPER;
        }
        Color color = null;
        String hex = s.getString("color");
        if (hex != null && hex.startsWith("#") && hex.length() == 7) {
            color = Color.fromRGB(Integer.parseInt(hex.substring(1), 16));
        }
        ConfigurationSection book = s.getConfigurationSection("book");
        return new ItemSpec(roomId, id, material, s.getString("name", id), s.getStringList("lore"),
                s.getBoolean("glow", false), color,
                book == null ? null : book.getString("title", "Libro"),
                book == null ? null : book.getString("author", "Desconocido"),
                book == null ? List.of() : book.getStringList("pages"));
    }

    public String tag() {
        return roomId + ":" + id;
    }

    public ItemStack create(int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BookMeta bookMeta && bookTitle != null) {
            bookMeta.setTitle(bookTitle.length() > 32 ? bookTitle.substring(0, 32) : bookTitle);
            bookMeta.setAuthor(bookAuthor);
            bookMeta.setGeneration(BookMeta.Generation.ORIGINAL);
            List<Component> components = new ArrayList<>();
            for (String page : pages) {
                components.add(Messages.parse(page));
            }
            bookMeta.addPages(components.toArray(new Component[0]));
        }
        if (meta instanceof PotionMeta potionMeta && color != null) {
            potionMeta.setColor(color);
        }
        if (meta instanceof LeatherArmorMeta leather && color != null) {
            leather.setColor(color);
        }
        meta.displayName(Messages.item(name));
        if (!lore.isEmpty()) {
            List<Component> loreLines = new ArrayList<>();
            for (String line : lore) {
                loreLines.add(Messages.item(line));
            }
            meta.lore(loreLines);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(EscapeRoomPlugin.get().keys().item, PersistentDataType.STRING, tag());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Returns the "roomId:itemId" tag of an item, or null. */
    public static String tagOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(EscapeRoomPlugin.get().keys().item, PersistentDataType.STRING);
    }

    public boolean matches(ItemStack stack) {
        return tag().equals(tagOf(stack));
    }
}
