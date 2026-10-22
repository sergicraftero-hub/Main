package com.sergicraftero.escapes.listener;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.instance.Instance;
import com.sergicraftero.escapes.lobby.Hotbar;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Protection of the lobby and the rooms, and routing of clicks/movement to games and the lobby.
 * Admins in creative mode with {@code escaperooms.build} can edit (map editor).
 */
public final class WorldListener implements Listener {

    private final EscapePlugin plugin;

    public WorldListener(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("escaperooms.build");
    }

    private boolean managed(Player p) {
        return plugin.lobby().inLobby(p) || p.getWorld() == plugin.instances().world();
    }

    // ───────────────────────── clicks ─────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!managed(p) || builder(p)) {
            return;
        }
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            // everything is handled once, for the main hand
            e.setCancelled(true);
            return;
        }
        Action action = e.getAction();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String hotbar = Hotbar.action(hand);
        Game game = plugin.games().of(p);

        if (action == Action.PHYSICAL) {
            Block b = e.getClickedBlock();
            if (b != null && (b.getType() == Material.FARMLAND || b.getType() == Material.TURTLE_EGG
                    || b.getType().name().contains("DRIPLEAF"))) {
                e.setCancelled(true);
            }
            return; // pressure plates & tripwires work normally
        }

        if (game != null) {
            boolean right = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
            if (e.getClickedBlock() != null) {
                if (game.interact(p, e.getClickedBlock(), right, hand)) {
                    e.setCancelled(true);
                    return;
                }
            }
            if ("notebook".equals(hotbar) && right) {
                e.setCancelled(true);
                plugin.ui().notebook(p);
                return;
            }
            Block b = e.getClickedBlock();
            if (b != null && !vanillaAllowed(b, right)) {
                e.setUseInteractedBlock(Event.Result.DENY);
            }
            if (hand.getType().isEdible() || hand.getType() == Material.ENDER_PEARL || hand.getType().name().endsWith("_BUCKET")
                    || hand.getType() == Material.FLINT_AND_STEEL || hand.getType().name().endsWith("SPAWN_EGG")) {
                e.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }

        // lobby
        if (hotbar != null && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            plugin.ui().action(p, hotbar);
            return;
        }
        if (e.getClickedBlock() != null) {
            e.setCancelled(true);
        }
    }

    /** Blocks players may use normally inside a room (doors, chests to loot, books to read...). */
    private static boolean vanillaAllowed(Block b, boolean right) {
        if (!right) {
            return true;
        }
        String n = b.getType().name();
        if (n.endsWith("_DOOR")) {
            return !n.startsWith("IRON");
        }
        if (n.endsWith("_TRAPDOOR")) {
            return !n.startsWith("IRON");
        }
        return n.endsWith("_FENCE_GATE") || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
                || n.endsWith("SHULKER_BOX") || n.equals("LECTERN") || n.endsWith("_BUTTON") || n.equals("LEVER")
                || n.equals("BELL");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!managed(p) || builder(p)) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e instanceof PlayerInteractAtEntityEvent) {
            return; // the plain event fires as well; handle once
        }
        Entity target = e.getRightClicked();
        Game game = plugin.games().of(p);
        if (game != null) {
            game.interactEntity(p, target, true, p.getInventory().getItemInMainHand());
            return;
        }
        String npc = plugin.lobby().npcId(target);
        if (npc != null) {
            ConfigurationSection s = plugin.lobby().npc(npc);
            if (s != null) {
                for (String line : s.getStringList("talk")) {
                    p.sendMessage(Msg.get("props.npc-says", "npc", Msg.parse(s.getString("name", npc)), "text", Msg.parse(line, "player", p.getName())));
                }
                plugin.ui().action(p, s.getString("action", "menu:main"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityAt(PlayerInteractAtEntityEvent e) {
        if (managed(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!(victim.getWorld() == plugin.instances().world() || plugin.lobby().world() == victim.getWorld())) {
            return;
        }
        if (e.getDamager() instanceof Player p && builder(p)) {
            return;
        }
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p && victim instanceof ItemFrame) {
            Game game = plugin.games().of(p);
            if (game != null) {
                game.interactEntity(p, victim, false, p.getInventory().getItemInMainHand());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getWorld() == plugin.instances().world() || e.getEntity().getWorld() == plugin.lobby().world()) {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && e.getEntity() instanceof Player p) {
                // the move handler teleports them back; never let them die
                p.setFallDistance(0);
            }
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && managed(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHanging(HangingBreakEvent e) {
        if (e.getEntity().getWorld() == plugin.instances().world() || e.getEntity().getWorld() == plugin.lobby().world()) {
            if (e.getCause() != HangingBreakEvent.RemoveCause.DEFAULT) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHangingByPlayer(org.bukkit.event.hanging.HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && builder(p)) {
            return;
        }
        if (e.getEntity().getWorld() == plugin.instances().world() || e.getEntity().getWorld() == plugin.lobby().world()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (managed(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (managed(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (managed(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ───────────────────────── items ─────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (managed(p) && !builder(p)) {
            e.setCancelled(true);
            if (plugin.games().of(p) != null) {
                Msg.actionBar(p, "game.no-drop");
            }
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (managed(p) && !builder(p) && (Hotbar.action(e.getMainHandItem()) != null || Hotbar.action(e.getOffHandItem()) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        if (plugin.lobby().inLobby(p) && !builder(p)) {
            e.setCancelled(true);
            return;
        }
        Game g = plugin.games().of(p);
        if (g != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> g.inventoryChanged(p));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !managed(p) || builder(p)) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof com.sergicraftero.escapes.gui.Menu) {
            return; // menus handle themselves
        }
        ItemStack current = e.getCurrentItem();
        ItemStack hotbarSwap = e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
        if (Hotbar.action(current) != null || Hotbar.action(e.getCursor()) != null || Hotbar.action(hotbarSwap) != null) {
            e.setCancelled(true); // lobby items & notebook stay put
            return;
        }
        Game g = plugin.games().of(p);
        if (g == null) {
            if (plugin.lobby().inLobby(p)) {
                e.setCancelled(true);
            }
            return;
        }
        // In rooms, containers are for taking things out: putting room items in could lose them.
        if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
            boolean clickedTop = e.getClickedInventory() == top;
            switch (e.getAction()) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> {
                    // fine anywhere
                }
                case MOVE_TO_OTHER_INVENTORY -> {
                    if (!clickedTop) {
                        e.setCancelled(true);
                    }
                }
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP, COLLECT_TO_CURSOR -> {
                    if (clickedTop || e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                        e.setCancelled(true);
                    }
                }
                default -> {
                    if (clickedTop) {
                        e.setCancelled(true);
                    }
                }
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> g.inventoryChanged(p));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !managed(p) || builder(p)) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof com.sergicraftero.escapes.gui.Menu) {
            return;
        }
        if (plugin.lobby().inLobby(p)) {
            e.setCancelled(true);
            return;
        }
        int topSize = top.getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize && top.getType() != InventoryType.CRAFTING) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ───────────────────────── movement ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        Game g = plugin.games().of(p);
        if (g != null && p.getWorld() == plugin.instances().world()) {
            g.moved(p, e.getTo());
            return;
        }
        if (plugin.lobby().inLobby(p)) {
            plugin.lobby().moved(p, e.getTo());
        }
    }

    /** Nobody may wander into a copy of a room they are not playing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (e.getTo().getWorld() != plugin.instances().world() || p.hasPermission("escaperooms.admin")) {
            return;
        }
        Instance target = plugin.instances().at(e.getTo());
        Game g = plugin.games().of(p);
        if (target == null || g == null || g.instance() != target) {
            if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN || g == null) {
                e.setCancelled(true);
            }
        }
    }
}
