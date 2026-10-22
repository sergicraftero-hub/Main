package com.sergicraftero.escapes.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * An item described in YAML. Used for room items (keys, clues, gadgets), menu buttons and rewards.
 * <pre>
 * material: TRIPWIRE_HOOK          # vanilla fallback (what Bedrock players see)
 * model: escape:key_red            # item model from the resource pack (optional)
 * name: "&lt;red&gt;Llave roja"
 * lore: ["Abre algo rojo..."]
 * glow: true
 * tags: [key]
 * book: {title: ..., author: ..., pages: [...]}   # written books
 * color: "#AA3311"                 # leather armour / potions
 * texture: base64                  # player heads
 * </pre>
 */
public record ItemSpec(String id, Material material, String model, String name, List<String> lore, boolean glow,
                       int amount, List<String> tags, String bookTitle, String bookAuthor, List<String> pages,
                       String color, String texture, boolean hideTooltip) {

    public static ItemSpec parse(String id, ConfigurationSection s) {
        if (s == null) {
            return simple(id, Material.STONE);
        }
        Material material = material(s.getString("material", "PAPER"));
        ConfigurationSection book = s.getConfigurationSection("book");
        return new ItemSpec(id, material, s.getString("model"), s.getString("name"), s.getStringList("lore"),
                s.getBoolean("glow", false), Math.max(1, s.getInt("amount", 1)), s.getStringList("tags"),
                book == null ? null : book.getString("title", "Libro"),
                book == null ? null : book.getString("author", "?"),
                book == null ? List.of() : book.getStringList("pages"),
                s.getString("color"), s.getString("texture"), s.getBoolean("hide-tooltip", false));
    }

    @SuppressWarnings("unchecked")
    public static ItemSpec parse(String id, Map<?, ?> map) {
        org.bukkit.configuration.MemoryConfiguration mem = new org.bukkit.configuration.MemoryConfiguration();
        ConfigurationSection s = mem.createSection("x", (Map<String, Object>) map);
        return parse(id, s);
    }

    public static ItemSpec simple(String id, Material material) {
        return new ItemSpec(id, material, null, null, List.of(), false, 1, List.of(), null, null, List.of(), null, null, false);
    }

    public static Material material(String name) {
        if (name == null) {
            return Material.PAPER;
        }
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        return m == null ? Material.PAPER : m;
    }

    public ItemSpec withName(String newName) {
        return new ItemSpec(id, material, model, newName, lore, glow, amount, tags, bookTitle, bookAuthor, pages, color,
                texture, hideTooltip);
    }

    public ItemSpec withLore(List<String> newLore) {
        return new ItemSpec(id, material, model, name, newLore, glow, amount, tags, bookTitle, bookAuthor, pages, color,
                texture, hideTooltip);
    }

    public ItemSpec withMaterial(Material m) {
        return new ItemSpec(id, m, model, name, lore, glow, amount, tags, bookTitle, bookAuthor, pages, color,
                texture, hideTooltip);
    }

    public ItemSpec withGlow(boolean g) {
        return new ItemSpec(id, material, model, name, lore, g, amount, tags, bookTitle, bookAuthor, pages, color,
                texture, hideTooltip);
    }

    public ItemStack create(Object... placeholders) {
        return create(amount, placeholders);
    }

    public ItemStack create(int count) {
        return create(count, new Object[0]);
    }

    @SuppressWarnings("deprecation") // setCustomModelData(Integer) still maps to the 1.21.4 component
    public ItemStack create(int count, Object... placeholders) {
        ItemStack item = new ItemStack(material, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            meta.displayName(Msg.item(name, placeholders));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(Msg.itemLines(lore, placeholders));
        }
        if (model != null && !model.isBlank()) {
            // Custom model data (not item_model): without the pack the vanilla look stays, never a missing texture.
            meta.setCustomModelData(modelData(model));
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
        if (hideTooltip) {
            meta.setHideTooltip(true);
        }
        if (id != null) {
            Keys.set(meta, Keys.ITEM_ID, id);
        }
        if (tags != null && !tags.isEmpty()) {
            Keys.set(meta, Keys.ITEM_TAGS, String.join(",", tags));
        }
        if (meta instanceof BookMeta bm && bookTitle != null) {
            bm.setTitle(Msg.strip(bookTitle));
            bm.setAuthor(Msg.strip(bookAuthor));
            List<net.kyori.adventure.text.Component> comps = new ArrayList<>();
            for (String page : pages) {
                comps.add(Msg.parse(page, placeholders));
            }
            Cosmetic.apply(() -> bm.addPages(comps.toArray(new net.kyori.adventure.text.Component[0])));
        }
        if (color != null) {
            Color c = Color.fromRGB(Integer.parseInt(color.replace("#", ""), 16));
            if (meta instanceof LeatherArmorMeta lm) {
                lm.setColor(c);
            } else if (meta instanceof PotionMeta pm) {
                pm.setColor(c);
            }
        }
        if (texture != null && meta instanceof SkullMeta sm) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes()), "er_head");
            profile.setProperty(new ProfileProperty("textures", texture));
            sm.setPlayerProfile(profile);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static int modelData(String model) {
        return com.sergicraftero.escapes.pack.ModelData.of(model);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String itemId = Keys.itemId(stack);
        if (itemId != null) {
            return itemId.equals(id);
        }
        return id != null && stack.getType().name().equalsIgnoreCase(id);
    }
}
