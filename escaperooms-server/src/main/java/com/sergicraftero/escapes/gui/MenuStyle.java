package com.sergicraftero.escapes.gui;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Msg;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Menu look. With the resource pack loaded (Java players), a painted background is drawn behind the chest
 * through a font glyph; Bedrock players and players without the pack get a plain title and glass fillers.
 */
public final class MenuStyle {

    /** Background glyph per menu kind (see resourcepack/assets/escape/font/gui.json). */
    private static final Map<String, String> BACKGROUNDS = Map.ofEntries(
            Map.entry("main", "\uE100"),
            Map.entry("rooms", "\uE101"),
            Map.entry("room", "\uE102"),
            Map.entry("shop", "\uE103"),
            Map.entry("profile", "\uE104"),
            Map.entry("keypad", "\uE105"),
            Map.entry("dial", "\uE106"),
            Map.entry("notebook", "\uE107"),
            Map.entry("generic", "\uE108"));

    /**
     * The title starts 8 px inside the chest: move −8 to the left edge, draw the 176 px background (advance 177),
     * then come back −169 so the title text sits where it normally does.
     */
    private static final String SHIFT_IN = "\uF808";
    private static final String SHIFT_BACK = "\uF880\uF820\uF808\uF801"; // −128 −32 −8 −1

    private MenuStyle() {
    }

    public static Component title(Player p, String kind, Component title) {
        EscapePlugin plugin = EscapePlugin.get();
        if (plugin == null || !plugin.pack().hasPack(p)) {
            return title;
        }
        String bg = BACKGROUNDS.getOrDefault(kind, BACKGROUNDS.get("generic"));
        Component glyph = Component.text(SHIFT_IN + bg + SHIFT_BACK).font(Key.key("escape", "gui")).color(NamedTextColor.WHITE);
        return glyph.append(title.colorIfAbsent(NamedTextColor.DARK_GRAY));
    }

    public static boolean painted(Player p) {
        EscapePlugin plugin = EscapePlugin.get();
        return plugin != null && plugin.pack().hasPack(p);
    }

    public static ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane(Material m, String name) {
        return ItemSpec.simple(null, m).withName(name).create();
    }

    public static ItemStack button(Material m, String name) {
        return ItemSpec.simple(null, m).withName(name).create();
    }

    public static ItemStack button(Material m, String name, List<String> lore) {
        return ItemSpec.simple(null, m).withName(name).withLore(lore).create();
    }

    public static ItemStack back() {
        return button(Material.ARROW, Msg.raw("menu.back"));
    }

    public static ItemStack close() {
        return button(Material.BARRIER, Msg.raw("menu.close"));
    }
}
