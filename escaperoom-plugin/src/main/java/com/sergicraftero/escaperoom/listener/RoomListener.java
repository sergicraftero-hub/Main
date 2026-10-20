package com.sergicraftero.escaperoom.listener;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.Puzzle;
import com.sergicraftero.escaperoom.puzzle.PuzzleState;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;

/** Block/entity protection for room regions and routing of interactions to puzzles. */
public final class RoomListener implements Listener {

    private final EscapeRoomPlugin plugin;

    public RoomListener(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    private Set<Material> allowedInteractions() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("protection.allowed-interactions")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                set.add(m);
            }
        }
        return set;
    }

    private boolean bypass(Player player) {
        return player.hasPermission("escaperoom.bypass") && !plugin.games().isPlaying(player);
    }

    private Room room(Location location) {
        return plugin.rooms().roomAt(location);
    }

    // ───────────────────────── interaction ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null) {
            return;
        }
        if (handleWandOrSign(event, block, player)) {
            return;
        }
        Room room = room(block.getLocation());
        if (room == null || bypass(player)) {
            return;
        }
        Game game = plugin.games().gameOf(player);
        boolean participant = game != null && game.room() == room && game.isPlaying(player.getUniqueId())
                && game.isRunning();
        if (!participant) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (block.getType() == Material.FARMLAND || block.getType() == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Puzzle puzzle = room.interactPuzzle(BlockPos.of(block));
        if (event.getHand() != EquipmentSlot.HAND) {
            if (puzzle != null || !allowedInteractions().contains(block.getType())) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }
        if (puzzle != null) {
            if (puzzle.state() == PuzzleState.LOCKED) {
                event.setCancelled(true);
                player.sendActionBar(plugin.messages().get("puzzle-locked"));
            } else if (puzzle.state() == PuzzleState.SOLVED) {
                event.setCancelled(true);
            } else {
                puzzle.onInteract(game, player, event);
            }
            return;
        }
        if (allowedInteractions().contains(block.getType())) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.WRITTEN_BOOK) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Join signs (anywhere) and the admin selection wand. */
    private boolean handleWandOrSign(PlayerInteractEvent event, Block block, Player player) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        ItemStack hand = event.getItem();
        if (hand != null && hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer()
                .has(plugin.keys().wand, PersistentDataType.BYTE) && player.hasPermission("escaperoom.admin.create")) {
            event.setCancelled(true);
            BlockPos pos = BlockPos.of(block);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.selections().pos1(player, block.getLocation());
                plugin.messages().send(player, "wand-pos1", Messages.ph("pos", pos.serialize()));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (player.isSneaking()) {
                    plugin.messages().send(player, "wand-coords", Messages.ph("pos", pos.serialize()),
                            Messages.ph("block", block.getType().getKey().getKey()));
                } else {
                    plugin.selections().pos2(player, block.getLocation());
                    plugin.messages().send(player, "wand-pos2", Messages.ph("pos", pos.serialize()));
                }
            }
            return true;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getState(false) instanceof TileState tile) {
            String roomId = tile.getPersistentDataContainer().get(plugin.keys().joinSign, PersistentDataType.STRING);
            if (roomId != null) {
                event.setCancelled(true);
                Room room = plugin.rooms().get(roomId);
                if (room == null) {
                    plugin.messages().send(player, "room-not-found", Messages.ph("room", roomId));
                } else if (player.hasPermission("escaperoom.play")) {
                    plugin.games().join(player, room);
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event) {
        if (room(event.getLectern().getLocation()) != null && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── blocks ─────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Room room = room(event.getBlock().getLocation());
        if (room != null && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Room room = room(event.getBlock().getLocation());
        Player player = event.getPlayer();
        if (room == null || bypass(player)) {
            return;
        }
        Game game = plugin.games().gameOf(player);
        Puzzle puzzle = room.placePuzzle(BlockPos.of(event.getBlock()));
        if (game != null && game.room() == room && game.isRunning() && game.isPlaying(player.getUniqueId())
                && puzzle != null && puzzle.state() == PuzzleState.ACTIVE) {
            puzzle.onBlockPlace(game, player, event);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (room(event.getBlock().getLocation()) != null && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (room(event.getBlock().getLocation()) != null && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // Keep the decoration static: no flowing water, fire, growth, decay or oxidation.

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (room(event.getToBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (room(event.getBlock().getLocation()) != null
                && (event.getPlayer() == null || !bypass(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> room(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> room(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && room(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── entities ─────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (room(event.getEntity().getLocation()) != null || isDecor(event.getEntity())) {
            if (event instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent byEntity
                    && byEntity.getRemover() instanceof Player p && bypass(p) && !isDecor(event.getEntity())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player victimPlayer) {
            if (plugin.games().isPlaying(victimPlayer) || room(victim.getLocation()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (room(victim.getLocation()) != null || isDecor(victim)) {
            if (event.getDamager() instanceof Player p && bypass(p) && !isDecor(victim)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if ((room(entity.getLocation()) != null || isDecor(entity)) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if ((room(event.getRightClicked().getLocation()) != null || isDecor(event.getRightClicked()))
                && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isDecor(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.keys().decor, PersistentDataType.STRING);
    }
}
