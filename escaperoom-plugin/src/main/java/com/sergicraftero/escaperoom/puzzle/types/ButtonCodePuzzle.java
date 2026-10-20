package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Buttons mapped to symbols. In ordered mode it is a keypad (the code must be typed in order);
 * otherwise it is a "press the right set" puzzle where any wrong press resets the progress.
 *
 * <pre>
 * type: buttons
 * buttons: {"1,2,3": "7", "1,2,4": "1"}
 * code: "7125"          # or a list for multi-character symbols
 * ordered: true
 * clear-button: "1,2,5"  # optional
 * </pre>
 */
public final class ButtonCodePuzzle extends AbstractPuzzle {

    private final Map<BlockPos, String> buttons = new LinkedHashMap<>();
    private final List<String> code = new ArrayList<>();
    private final boolean ordered;
    private final BlockPos clearButton;
    private final boolean showInput;
    private final List<String> input = new ArrayList<>();
    private final Set<BlockPos> interactables = new HashSet<>();

    public ButtonCodePuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "buttons", s, logger);
        positionMap(s, "buttons").forEach((pos, symbol) -> buttons.put(pos, String.valueOf(symbol)));
        if (s.isList("code")) {
            code.addAll(s.getStringList("code"));
        } else {
            for (char c : s.getString("code", "").toCharArray()) {
                code.add(String.valueOf(c));
            }
        }
        ordered = s.getBoolean("ordered", true);
        clearButton = s.contains("clear-button") ? BlockPos.parse(s.getString("clear-button")) : null;
        showInput = s.getBoolean("show-input", ordered);
        interactables.addAll(buttons.keySet());
        if (clearButton != null) {
            interactables.add(clearButton);
        }
    }

    @Override
    public Set<BlockPos> interactables() {
        return interactables;
    }

    @Override
    public void reset(Game game) {
        input.clear();
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || (block.getBlockData() instanceof Powerable p && p.isPowered())) {
            return;
        }
        BlockPos pos = BlockPos.of(block);
        if (pos.equals(clearButton)) {
            input.clear();
            sound(game, block.getLocation(), "block.stone_button.click_off", 0.8f, 0.6f);
            showInput(game);
            return;
        }
        String symbol = buttons.get(pos);
        if (symbol == null) {
            return;
        }
        if (ordered) {
            input.add(symbol);
            sound(game, block.getLocation(), "block.note_block.hat", 0.8f, 1.2f + 0.1f * input.size());
            showInput(game);
            if (input.size() >= code.size()) {
                if (input.equals(code)) {
                    solve(game, player);
                } else {
                    fail(game, block);
                }
            }
            return;
        }
        if (!code.contains(symbol)) {
            fail(game, block);
            return;
        }
        if (!input.contains(symbol)) {
            input.add(symbol);
            sound(game, block.getLocation(), "block.note_block.chime", 0.8f, 0.8f + 0.2f * input.size());
        }
        if (new HashSet<>(input).containsAll(code)) {
            solve(game, player);
        }
    }

    private void fail(Game game, Block block) {
        input.clear();
        sound(game, block.getLocation(), "block.note_block.bass", 1f, 0.5f);
        game.msg("puzzle-wrong-code", Messages.ph("puzzle", displayName()));
    }

    private void showInput(Game game) {
        if (!showInput) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.size(); i++) {
            sb.append(i < input.size() ? input.get(i) : "_");
            if (i < code.size() - 1) {
                sb.append(' ');
            }
        }
        game.actionBar(game.plugin().messages().get("puzzle-code-input", Messages.ph("code", sb.toString())));
    }
}
