package com.sergicraftero.escapes.room;

import com.sergicraftero.escapes.util.ItemSpec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** Creates room items by id, falling back to vanilla materials ("paper", "minecraft:clock"). */
public final class ItemService {

    public ItemStack create(RoomDef room, String id, int amount) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.startsWith("item:") ? id.substring(5) : id;
        if (room != null) {
            ItemSpec spec = room.items.get(key);
            if (spec != null) {
                return spec.create(amount);
            }
        }
        Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        if (m == null || !m.isItem() || m.isAir()) {
            return null;
        }
        return new ItemStack(m, Math.max(1, amount));
    }

    /** Container specs used in templates: "item:red_key", "paper*3", "minecraft:paper:3". */
    public ItemStack resolve(RoomDef room, String spec) {
        if (spec == null) {
            return null;
        }
        String s = spec.trim();
        int amount = 1;
        int star = s.lastIndexOf('*');
        if (star > 0) {
            amount = Integer.parseInt(s.substring(star + 1).trim());
            s = s.substring(0, star).trim();
        } else if (s.startsWith("minecraft:") && s.split(":").length == 3) {
            String[] p = s.split(":");
            amount = Integer.parseInt(p[2]);
            s = p[0] + ":" + p[1];
        }
        return create(room, s, amount);
    }
}
