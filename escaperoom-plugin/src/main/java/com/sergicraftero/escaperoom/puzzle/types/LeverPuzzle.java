package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lever combination. Each lever has a target state; optionally every lever mirrors its state on
 * one or more lamps (redstone lamps or any {@link Lightable}) without needing wiring.
 *
 * <pre>
 * type: levers
 * levers: {"10,64,10": true, "11,64,10": false}
 * lamps: {"10,64,10": ["10,70,10"]}
 * </pre>
 */
public final class LeverPuzzle extends AbstractPuzzle {

    private final Map<BlockPos, Boolean> target = new LinkedHashMap<>();
    private final Map<BlockPos, List<BlockPos>> lamps = new LinkedHashMap<>();

    public LeverPuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "levers", s, logger);
        positionMap(s, "levers").forEach((pos, value) -> target.put(pos, Boolean.parseBoolean(String.valueOf(value))));
        positionMap(s, "lamps").forEach((pos, value) -> {
            List<BlockPos> list = new ArrayList<>();
            if (value instanceof List<?> raw) {
                raw.forEach(o -> list.add(BlockPos.parse(String.valueOf(o))));
            } else {
                list.add(BlockPos.parse(String.valueOf(value)));
            }
            lamps.put(pos, list);
        });
    }

    @Override
    public Set<BlockPos> interactables() {
        return target.keySet();
    }

    @Override
    public void reset(Game game) {
        refreshLamps(game);
    }

    @Override
    public void onActivate(Game game) {
        refreshLamps(game);
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        int runId = game.runId();
        Bukkit.getScheduler().runTask(game.plugin(), () -> {
            if (game.runId() != runId || !game.isRunning()) {
                return;
            }
            refreshLamps(game);
            sound(game, event.getClickedBlock().getLocation(), "block.lever.click", 0.6f, 0.8f);
            if (matches(game)) {
                solve(game, player);
            }
        });
    }

    @Override
    public void tick(Game game, long tick) {
        if (tick % 10 == 0) {
            refreshLamps(game);
        }
    }

    private boolean isOn(Game game, BlockPos pos) {
        return pos.block(game.world()).getBlockData() instanceof Powerable p && p.isPowered();
    }

    private boolean matches(Game game) {
        for (Map.Entry<BlockPos, Boolean> e : target.entrySet()) {
            if (isOn(game, e.getKey()) != e.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void refreshLamps(Game game) {
        for (Map.Entry<BlockPos, List<BlockPos>> e : lamps.entrySet()) {
            boolean on = isOn(game, e.getKey());
            for (BlockPos lampPos : e.getValue()) {
                Block lamp = lampPos.block(game.world());
                if (lamp.getBlockData() instanceof Lightable lightable && lightable.isLit() != on) {
                    lightable.setLit(on);
                    lamp.setBlockData(lightable, false);
                }
            }
        }
    }
}
