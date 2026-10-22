package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.game.PropManager;
import com.sergicraftero.escapes.gui.Menu;
import com.sergicraftero.escapes.gui.MenuStyle;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick the right option in a menu: accuse the murderer, cut the right wire, choose the true statement.
 * Opened from a block ({@code at}) or by talking to an NPC prop ({@code npc}).
 * <pre>
 * type: choice
 * npc: inspector              # or at: "@hall.board"
 * title: "¿Quién es el culpable?"
 * intro: ["El inspector te mira fijamente..."]
 * options:
 *   - {material: PLAYER_HEAD, name: "La condesa", lore: ["..."], correct: false, reply: "Tenía coartada..."}
 *   - {material: PLAYER_HEAD, name: "El revisor", correct: true}
 * </pre>
 */
public final class ChoicePuzzle extends Puzzle {

    private record Option(ItemSpec item, boolean correct, String reply) {
    }

    private List<Block> blocks;
    private final List<Option> options = new ArrayList<>();

    public ChoicePuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
        int i = 0;
        for (ConfigurationSection o : Yml.sections(def.section(), "options")) {
            options.add(new Option(ItemSpec.parse("option_" + (i++), o), o.getBoolean("correct", false), o.getString("reply")));
        }
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        open(p);
        return true;
    }

    @Override
    public boolean ownsEntity(Entity e) {
        String npc = str("npc", null);
        return npc != null && npc.equals(PropManager.propId(e));
    }

    @Override
    public boolean onEntity(Player p, Entity e, boolean right, ItemStack hand) {
        open(p);
        return true;
    }

    private void open(Player p) {
        for (String line : list("intro")) {
            p.sendMessage(Msg.parse(line, "player", p.getName()));
        }
        int rows = Math.max(3, Math.min(6, (options.size() + 8) / 9 + 2));
        Menu menu = new Menu(rows, MenuStyle.title(p, "generic", Msg.parse(str("title", "<dark_gray>Elige"))));
        int start = 9 + Math.max(0, (9 - Math.min(9, options.size())) / 2);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int slot = options.size() <= 9 ? start + i : 9 + i;
            menu.set(slot, o.item().create(), c -> pick(p, o));
        }
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private void pick(Player p, Option o) {
        p.closeInventory();
        if (o.reply() != null) {
            p.sendMessage(Msg.parse(o.reply(), "player", p.getName()));
        }
        if (o.correct()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
            solve(p);
        } else {
            tell(p, "wrong-message", "puzzle.choice-wrong");
            fail(p);
        }
    }
}
