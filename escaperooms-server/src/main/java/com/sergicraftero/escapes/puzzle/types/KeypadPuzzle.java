package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.gui.Menu;
import com.sergicraftero.escapes.gui.MenuStyle;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Text;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A code entered in a menu: a safe, a padlock, a computer, a rune lock.
 * <pre>
 * type: keypad
 * at: "@study.safe"
 * code: "1789"
 * style: digits | letters | dial | symbols
 * symbols:                    # style symbols/dial: the keys (default 0-9)
 *   sol: {material: SUNFLOWER, name: "Sol"}
 *   luna: {material: ENDER_EYE, name: "Luna"}
 * title: "Caja fuerte"
 * </pre>
 * {@code code} is a string (digits/letters) or, for symbols, a list of symbol ids.
 */
public final class KeypadPuzzle extends Puzzle {

    private static final String LETTERS = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";

    private List<Block> blocks;
    private String style;
    private List<String> code;
    private final List<String> keys = new ArrayList<>();
    private final List<ItemSpec> keySpecs = new ArrayList<>();

    public KeypadPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
        style = str("style", "digits").toLowerCase(Locale.ROOT);
        ConfigurationSection symbols = def.section().getConfigurationSection("symbols");
        if (symbols != null) {
            for (String k : symbols.getKeys(false)) {
                keys.add(k);
                keySpecs.add(ItemSpec.parse(k, symbols.getConfigurationSection(k)));
            }
        } else if (style.equals("letters")) {
            for (char c : LETTERS.toCharArray()) {
                keys.add(String.valueOf(c));
                keySpecs.add(ItemSpec.simple(null, Material.PAPER).withName("<white><b>" + c));
            }
        } else {
            for (int i = 0; i <= 9; i++) {
                keys.add(String.valueOf(i));
                keySpecs.add(ItemSpec.simple(null, Material.PAPER).withName("<white><b>" + i));
            }
        }
        code = new ArrayList<>();
        if (def.section().isList("code")) {
            for (String s : Yml.strings(def.section(), "code")) {
                code.add(s);
            }
        } else {
            String raw = str("code", "0000").toUpperCase(Locale.ROOT).replace(" ", "");
            for (int i = 0; i < raw.length(); i++) {
                code.add(String.valueOf(raw.charAt(i)));
            }
        }
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        p.playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.6f);
        if (style.equals("dial")) {
            new DialMenu(p).open(p);
        } else {
            new PadMenu(p).open(p);
        }
        return true;
    }

    private void check(Player p, List<String> entered) {
        if (normalized(entered).equals(normalized(code))) {
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.2f);
            solve(p);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
            tell(p, "wrong-message", "puzzle.wrong-code");
            fail(p);
        }
    }

    private static List<String> normalized(List<String> l) {
        List<String> out = new ArrayList<>();
        for (String s : l) {
            out.add(Text.normalize(s));
        }
        return out;
    }

    private ItemStack keyItem(int index, int amountHint) {
        ItemStack it = keySpecs.get(index).create();
        if (style.equals("digits") && amountHint > 0) {
            it.setAmount(amountHint); // Bedrock players read the digit from the stack size too
        }
        return it;
    }

    // ───────────────────────── keypad ─────────────────────────

    private final class PadMenu extends Menu {
        private final List<String> entered = new ArrayList<>();
        private final Player player;

        PadMenu(Player p) {
            super(6, MenuStyle.title(p, "keypad", Msg.parse(str("title", "<dark_gray>Introduce el código"))));
            this.player = p;
            draw();
        }

        private void draw() {
            clear();
            // display row
            for (int i = 0; i < 9; i++) {
                if (i < code.size()) {
                    if (i < entered.size()) {
                        int k = keys.indexOf(entered.get(i));
                        set(i, k >= 0 ? keyItem(k, digitAmount(k)) : MenuStyle.pane(Material.WHITE_STAINED_GLASS_PANE, entered.get(i)));
                    } else {
                        set(i, MenuStyle.pane(Material.GRAY_STAINED_GLASS_PANE, "<gray>_"));
                    }
                } else {
                    set(i, MenuStyle.pane(Material.BLACK_STAINED_GLASS_PANE, " "));
                }
            }
            int[] slots = keySlots();
            for (int k = 0; k < keys.size() && k < slots.length; k++) {
                int key = k;
                set(slots[k], keyItem(k, digitAmount(k)), c -> press(key));
            }
            set(45, MenuStyle.button(Material.RED_CONCRETE, Msg.raw("puzzle.keypad-clear")), c -> {
                entered.clear();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.6f);
                draw();
            });
            set(46, MenuStyle.button(Material.ORANGE_CONCRETE, Msg.raw("puzzle.keypad-delete")), c -> {
                if (!entered.isEmpty()) {
                    entered.remove(entered.size() - 1);
                }
                draw();
            });
            set(53, MenuStyle.button(Material.LIME_CONCRETE, Msg.raw("puzzle.keypad-enter")), c -> check(player, entered));
            fill(MenuStyle.filler());
        }

        private int digitAmount(int k) {
            if (!style.equals("digits")) {
                return 0;
            }
            int d = Integer.parseInt(keys.get(k));
            return d == 0 ? 1 : d;
        }

        private int[] keySlots() {
            if (style.equals("digits") && keys.size() == 10) {
                // phone layout: 1 2 3 / 4 5 6 / 7 8 9 / 0
                return new int[]{40, 12, 13, 14, 21, 22, 23, 30, 31, 32};
            }
            int[] s = new int[36];
            for (int i = 0; i < 36; i++) {
                s[i] = 9 + i;
            }
            return s;
        }

        private void press(int key) {
            if (entered.size() >= code.size()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                return;
            }
            entered.add(keys.get(key));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f + entered.size() * 0.1f);
            if (entered.size() == code.size() && bool("auto-submit", true)) {
                check(player, entered);
                if (isActive()) {
                    entered.clear();
                    draw();
                }
                return;
            }
            draw();
        }
    }

    // ───────────────────────── dial lock ─────────────────────────

    private final class DialMenu extends Menu {
        private final int[] values;
        private final Player player;

        DialMenu(Player p) {
            super(5, MenuStyle.title(p, "dial", Msg.parse(str("title", "<dark_gray>Cerradura de combinación"))));
            this.player = p;
            this.values = new int[Math.min(code.size(), 9)];
            draw();
        }

        private void draw() {
            clear();
            int n = values.length;
            int start = (9 - n) / 2;
            for (int i = 0; i < n; i++) {
                int col = i;
                set(9 + start + i, MenuStyle.button(Material.LIME_STAINED_GLASS_PANE, "<green>▲"), c -> turn(col, 1));
                set(18 + start + i, keyItem(values[i], 0), c -> turn(col, c.right() ? -1 : 1));
                set(27 + start + i, MenuStyle.button(Material.RED_STAINED_GLASS_PANE, "<red>▼"), c -> turn(col, -1));
            }
            set(40, MenuStyle.button(Material.TRIPWIRE_HOOK, Msg.raw("puzzle.dial-try")), c -> {
                List<String> entered = new ArrayList<>();
                for (int v : values) {
                    entered.add(keys.get(v));
                }
                check(player, entered);
            });
            fill(MenuStyle.filler());
        }

        private void turn(int col, int dir) {
            values[col] = Math.floorMod(values[col] + dir, keys.size());
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.8f);
            draw();
        }
    }
}
