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

import java.util.List;
import java.util.Random;

/**
 * 3×3 sliding tiles (8 tiles and a hole) in a menu. The board state is shared by the team; it is scrambled
 * with random legal moves so it is always solvable.
 * <pre>
 * type: slide
 * at: "@study.puzzlebox"
 * tiles: [RED_WOOL, ORANGE_WOOL, ...]   # optional 8 materials, in solved order
 * moves: 40                              # scramble length
 * </pre>
 */
public final class SlidePuzzle extends Puzzle {

    private static final int N = 3;
    private final int[] board = new int[N * N]; // value 0 = hole, 1..8 tiles; solved = 1..8,0
    private List<Block> blocks;
    private Material[] tiles = {Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
            Material.CYAN_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL};

    public SlidePuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
        List<String> custom = list("tiles");
        if (custom.size() >= 8) {
            for (int i = 0; i < 8; i++) {
                tiles[i] = ItemSpec.material(custom.get(i));
            }
        }
        for (int i = 0; i < 8; i++) {
            board[i] = i + 1;
        }
        board[8] = 0;
        Random r = new Random(integer("seed", 3));
        int moves = Math.max(10, integer("moves", 40));
        int last = -1;
        for (int i = 0; i < moves; i++) {
            int hole = hole();
            int[] nb = neighbours(hole);
            int pick;
            do {
                pick = nb[r.nextInt(nb.length)];
            } while (pick == last && nb.length > 1);
            last = hole;
            swap(hole, pick);
        }
        if (solved()) {
            swap(hole(), neighbours(hole())[0]);
        }
    }

    private int hole() {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0) {
                return i;
            }
        }
        return 8;
    }

    private int[] neighbours(int i) {
        int r = i / N;
        int c = i % N;
        int[] tmp = new int[4];
        int n = 0;
        if (r > 0) {
            tmp[n++] = i - N;
        }
        if (r < N - 1) {
            tmp[n++] = i + N;
        }
        if (c > 0) {
            tmp[n++] = i - 1;
        }
        if (c < N - 1) {
            tmp[n++] = i + 1;
        }
        return java.util.Arrays.copyOf(tmp, n);
    }

    private void swap(int a, int b) {
        int t = board[a];
        board[a] = board[b];
        board[b] = t;
    }

    private boolean solved() {
        for (int i = 0; i < 8; i++) {
            if (board[i] != i + 1) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        new BoardMenu(p).open(p);
        return true;
    }

    private final class BoardMenu extends Menu {
        private final Player player;

        BoardMenu(Player p) {
            super(5, MenuStyle.title(p, "generic", Msg.parse(str("title", "<dark_gray>Desliza las piezas"))));
            this.player = p;
            draw();
        }

        private void draw() {
            clear();
            for (int i = 0; i < board.length; i++) {
                int cell = i;
                int slot = 9 + (i / N) * 9 + 3 + (i % N);
                ItemStack face = board[i] == 0 ? MenuStyle.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ")
                        : ItemSpec.simple(null, tiles[board[i] - 1]).withName("<white><b>" + board[i]).create();
                if (board[i] != 0) {
                    face.setAmount(board[i]);
                }
                set(slot, face, c -> move(cell));
            }
            fill(MenuStyle.filler());
        }

        private void move(int cell) {
            if (!isActive()) {
                player.closeInventory();
                return;
            }
            int hole = hole();
            for (int nb : neighbours(hole)) {
                if (nb == cell) {
                    swap(hole, cell);
                    player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1.3f);
                    if (solved()) {
                        player.closeInventory();
                        solve(player);
                        return;
                    }
                    draw();
                    return;
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }
}
