package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.gui.Menu;
import com.sergicraftero.escapes.gui.MenuStyle;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Card-pairs memory game in a menu. Each player who opens it gets a fresh shuffled board.
 * <pre>
 * type: memory
 * at: "@arcade.cabinet"
 * pairs: 8
 * icons: [DIAMOND, EMERALD, GOLD_INGOT, ...]   # optional
 * max-mistakes: 0                              # 0 = unlimited
 * </pre>
 */
public final class MemoryPuzzle extends Puzzle {

    private static final List<Material> DEFAULT_ICONS = List.of(Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.AMETHYST_SHARD, Material.PRISMARINE_CRYSTALS,
            Material.BLAZE_POWDER, Material.ENDER_PEARL, Material.SLIME_BALL, Material.HEART_OF_THE_SEA, Material.NETHER_STAR);

    private List<Block> blocks;

    public MemoryPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        new Board(p).open(p);
        return true;
    }

    private final class Board extends Menu {
        private final Player player;
        private final List<Material> cards = new ArrayList<>();
        private final boolean[] matched;
        private int first = -1;
        private int second = -1;
        private int mistakes;
        private boolean busy;

        Board(Player p) {
            super(5, MenuStyle.title(p, "generic", 5, Msg.parse(str("title", "<dark_gray>Memoria"))));
            this.player = p;
            int pairs = Math.max(2, Math.min(12, integer("pairs", 8)));
            List<Material> icons = new ArrayList<>();
            for (String s : list("icons")) {
                icons.add(ItemSpec.material(s));
            }
            if (icons.size() < pairs) {
                icons = DEFAULT_ICONS;
            }
            for (int i = 0; i < pairs; i++) {
                cards.add(icons.get(i % icons.size()));
                cards.add(icons.get(i % icons.size()));
            }
            Collections.shuffle(cards, new Random());
            matched = new boolean[cards.size()];
            draw();
        }

        private int slot(int card) {
            // centred 6-wide grid on rows 1..4
            int row = card / 6;
            int col = card % 6;
            return 9 + row * 9 + 1 + col;
        }

        private void draw() {
            clear();
            for (int i = 0; i < cards.size(); i++) {
                int card = i;
                boolean shown = matched[i] || i == first || i == second;
                ItemStack face = shown ? ItemSpec.simple(null, cards.get(i)).withName("<yellow>" + cards.get(i).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).create()
                        : MenuStyle.button(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>?");
                set(slot(i), face, c -> flip(card));
            }
            fill(MenuStyle.filler());
        }

        private void flip(int card) {
            if (busy || matched[card] || card == first) {
                return;
            }
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
            if (first < 0) {
                first = card;
                draw();
                return;
            }
            second = card;
            draw();
            if (cards.get(first) == cards.get(second)) {
                matched[first] = true;
                matched[second] = true;
                first = -1;
                second = -1;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
                boolean all = true;
                for (boolean m : matched) {
                    all &= m;
                }
                if (all) {
                    player.closeInventory();
                    solve(player);
                    return;
                }
                draw();
            } else {
                busy = true;
                mistakes++;
                int max = integer("max-mistakes", 0);
                game.later(() -> {
                    busy = false;
                    first = -1;
                    second = -1;
                    if (max > 0 && mistakes > max) {
                        player.closeInventory();
                        Msg.send(player, "puzzle.memory-failed");
                        fail(player);
                        return;
                    }
                    draw();
                }, 14);
            }
        }
    }
}
