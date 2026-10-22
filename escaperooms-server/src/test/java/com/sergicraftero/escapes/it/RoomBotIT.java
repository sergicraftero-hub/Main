package com.sergicraftero.escapes.it;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.game.PropManager;
import com.sergicraftero.escapes.gui.Menu;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Text;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A QA bot that plays a whole room on MockBukkit through the real event handlers: it clicks blocks,
 * opens menus and clicks their slots, places items in frames, answers in chat and walks into regions.
 * It only knows the room YAML (like a player who read the solutions) — the game code decides everything.
 */
class RoomBotIT {

    private ServerMock server;
    private EscapePlugin plugin;
    private PlayerMock player;
    private Game game;
    private final Set<String> looted = new HashSet<>();
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        try {
            server = MockBukkit.mock(new TestServer());
            plugin = MockBukkit.load(EscapePlugin.class);
            player = new TestPlayer(server, "Bot");
            server.addPlayer(player);
            server.getScheduler().performTicks(40);
        } catch (org.opentest4j.TestAbortedException e) {
            throw new AssertionError("MockBukkit lacks a feature (setup): " + e, e);
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tutorial", "alquimista", "expreso"})
    void botEscapes(String roomId) throws Exception {
        try {
            play(roomId);
        } catch (org.opentest4j.TestAbortedException e) {
            // MockBukkit reports missing features as "aborted": surface them as failures
            throw new AssertionError("MockBukkit lacks a feature: " + e, e);
        }
    }

    private void play(String roomId) throws Exception {
        RoomDef room = plugin.rooms().get(roomId);
        assertNotNull(room, roomId);
        player.addAttachment(plugin, "escaperooms.bypass.access", true);
        plugin.games().playPrivate(player, room);
        for (int i = 0; i < 200 && plugin.games().of(player) == null; i++) {
            server.getScheduler().performTicks(1);
        }
        game = plugin.games().of(player);
        assertNotNull(game, "the game never started");
        ticks((long) room.intro.size() * room.introInterval + 60);
        assertEquals(Game.State.RUNNING, game.state(), "game not running after the intro");

        int stuck = 0;
        int solvedBefore = -1;
        for (int round = 0; round < 120 && game.state() == Game.State.RUNNING; round++) {
            loot();
            for (Puzzle pz : new ArrayList<>(game.puzzles().values())) {
                if (pz.isActive() && game.state() == Game.State.RUNNING) {
                    try {
                        solve(pz);
                    } catch (RuntimeException e) {
                        throw new AssertionError("bot failed on " + pz.id() + ": " + e, e);
                    }
                    ticks(5);
                }
            }
            ticks(10);
            int solved = (int) game.puzzles().values().stream().filter(Puzzle::isSolved).count();
            stuck = solved == solvedBefore ? stuck + 1 : 0;
            solvedBefore = solved;
            if (game.puzzles().values().stream().noneMatch(Puzzle::isActive)
                    && game.puzzles().values().stream().anyMatch(Puzzle::isSolved)) {
                break; // everything solved: the win is on its way (win-delay)
            }
            if (stuck >= 4) {
                List<String> active = new ArrayList<>();
                game.puzzles().values().stream().filter(Puzzle::isActive).forEach(p -> active.add(p.id()));
                fail("[" + roomId + "] stuck; active " + active + "; inventory " + inventory() + "; log " + log);
            }
        }
        ticks(200);
        assertTrue(game.won(), "[" + roomId + "] game not won (state " + game.state() + ") log " + log);
        ticks(400);
        assertTrue(plugin.lobby().inLobby(player), "player not back in the lobby after the game");
        PlayerProfile prof = plugin.profiles().get(player);
        assertEquals(1, prof.room(roomId).wins);
        assertTrue(prof.xp > 0, "no XP rewarded");
        System.out.println("[" + roomId + "] bot escaped in " + Text.time(game.elapsed()) + " (game time) — "
                + game.puzzles().size() + " puzzles, level " + plugin.progress().level(prof.xp) + ", keys " + prof.keys);
    }

    // ───────────────────────── helpers ─────────────────────────

    private void ticks(long n) {
        server.getScheduler().performTicks(n);
    }

    private List<String> inventory() {
        List<String> out = new ArrayList<>();
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && Keys.itemId(it) != null) {
                out.add(Keys.itemId(it) + "x" + it.getAmount());
            }
        }
        return out;
    }

    private void goTo(Location l) {
        Location target = l.clone();
        target.setY(Math.floor(target.getY()));
        player.teleport(target);
    }

    private void click(Block b) {
        goTo(b.getLocation().add(0.5, 1, 0.5));
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), b, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        ticks(1);
    }

    private void click(String ref) {
        click(game.block(ref));
    }

    private void clickEntity(Entity e) {
        goTo(e.getLocation());
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, e, EquipmentSlot.HAND));
        ticks(1);
    }

    private boolean hold(String itemId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && itemId.equals(Keys.itemId(contents[i]))) {
                ItemStack it = contents[i];
                ItemStack hand = player.getInventory().getItemInMainHand();
                player.getInventory().setItem(i, hand);
                player.getInventory().setItemInMainHand(it);
                return true;
            }
        }
        return false;
    }

    private void emptyHand() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && !hand.getType().isAir() && Keys.get(hand, Keys.HOTBAR) == null) {
            player.getInventory().setItemInMainHand(null);
            player.getInventory().addItem(hand);
        }
    }

    private InventoryHolder openMenu() {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(); // MockBukkit has no top inventory when nothing is open
    }

    private void menuClick(int slot) {
        player.simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, slot);
        ticks(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws ReflectiveOperationException {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ───────────────────────── looting ─────────────────────────

    /** Searches spots, empties containers and crafts whatever the workbenches offer — like a curious player. */
    private void loot() throws Exception {
        for (ConfigurationSection prop : game.room().props) {
            String id = prop.getString("id", "");
            String type = prop.getString("type", "");
            if (looted.contains(id) || !prop.contains("at")) {
                continue;
            }
            switch (type) {
                case "search" -> {
                    if (prop.contains("requires-item") && !hasItem(prop.getString("requires-item"))) {
                        continue;
                    }
                    if (!game.props().has(id) || !requirementsMet(prop)) {
                        continue;
                    }
                    if (prop.contains("requires-item")) {
                        hold(prop.getString("requires-item"));
                    }
                    click(prop.getString("at"));
                    looted.add(id);
                    log.add("searched " + id);
                    emptyHand();
                }
                case "container" -> {
                    if (!requirementsMet(prop)) {
                        continue;
                    }
                    for (Block b : game.blocks(prop.getString("at"))) {
                        if (b.getState() instanceof Container c) {
                            takeAll(c.getInventory());
                        }
                    }
                    looted.add(id);
                    log.add("looted " + id);
                }
                case "workbench" -> {
                    click(prop.getString("at"));
                    if (openMenu() instanceof Menu) {
                        Inventory top = player.getOpenInventory().getTopInventory();
                        ItemStack first = top.getItem(10);
                        if (first != null && first.getType() != Material.BARRIER && first.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                            menuClick(10);
                            log.add("crafted at " + id + " -> " + inventory());
                        }
                        player.closeInventory();
                    }
                }
                default -> {
                }
            }
        }
    }

    private boolean requirementsMet(ConfigurationSection prop) {
        for (String r : Yml.strings(prop, "requires")) {
            if (!game.isSolved(r)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItem(String id) {
        return Game.has(player, id);
    }

    private void takeAll(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                player.getInventory().addItem(it.clone());
                inv.setItem(i, null);
            }
        }
        game.inventoryChanged(player);
    }

    // ───────────────────────── solving ─────────────────────────

    private void solve(Puzzle pz) throws Exception {
        PuzzleDef def = pz.def();
        ConfigurationSection s = def.section();
        switch (def.type()) {
            case "interact" -> {
                String item = s.getString("item");
                if (item != null && !hold(item)) {
                    return;
                }
                click(def.list("at").get(0));
                emptyHand();
            }
            case "keypad" -> keypad(pz);
            case "levers" -> {
                List<Block> levers = game.blocks(def.list("levers"));
                String pattern = s.getString("pattern", "");
                for (int i = 0; i < levers.size() && i < pattern.length(); i++) {
                    boolean on = levers.get(i).getBlockData() instanceof Powerable pw && pw.isPowered();
                    if (on != (pattern.charAt(i) == '1')) {
                        click(levers.get(i));
                    }
                }
            }
            case "sequence" -> {
                List<Block> buttons = game.blocks(def.list("buttons"));
                for (String o : def.list("order")) {
                    click(buttons.get(Integer.parseInt(o.trim()) - 1));
                }
            }
            case "simon" -> simon(pz);
            case "plates" -> {
                String weight = s.getString("weight-item");
                for (Block plate : game.blocks(def.list("plates"))) {
                    if (weight != null && hold(weight)) {
                        click(plate);
                    }
                }
                emptyHand();
                ticks(80);
            }
            case "frames" -> frames(pz);
            case "deliver" -> {
                List<String> done = new ArrayList<>(field(pz, "delivered"));
                for (String item : def.list("items")) {
                    if (done.remove(item)) {
                        continue; // already in from an earlier round
                    }
                    if (!hold(item)) {
                        return;
                    }
                    click(def.list("at").get(0));
                }
                emptyHand();
            }
            case "answer" -> {
                if (!def.list("at").isEmpty()) {
                    click(def.list("at").get(0));
                }
                game.chat(player, def.list("answers").get(0));
            }
            case "reach" -> {
                BoundingBox box = game.box(s.getString("region", s.getString("at")));
                Location target = new Location(game.instance().world(), box.getCenterX(), box.getMinY(), box.getCenterZ());
                player.teleport(target.clone().add(4, 0, 0));
                player.simulatePlayerMove(target);
                ticks(5);
            }
            case "lightsout" -> {
                Block[][] cells = field(pz, "cells");
                int w = field(pz, "w");
                for (String c : def.list("scramble")) {
                    int i = Integer.parseInt(c.trim()) - 1;
                    click(cells[i / w][i % w]);
                }
            }
            case "melody" -> {
                List<Block> keys = game.blocks(def.list("keys"));
                for (String m : def.list("melody")) {
                    click(keys.get(Integer.parseInt(m.trim()) - 1));
                }
            }
            case "choice" -> choice(pz);
            case "memory" -> memory(pz);
            case "slide" -> slide(pz);
            default -> {
                // obtain / gate solve themselves
            }
        }
    }

    private void keypad(Puzzle pz) throws Exception {
        Block kb = game.block(pz.def().list("at").get(0));
        click(kb);
        InventoryHolder menu = openMenu();
        assertTrue(menu instanceof Menu, pz.id() + ": keypad menu didn't open");
        List<String> keys = field(pz, "keys");
        List<String> code = field(pz, "code");
        String style = field(pz, "style");
        if (style.equals("dial")) {
            int n = code.size();
            int start = (9 - n) / 2;
            for (int i = 0; i < n; i++) {
                int target = keys.indexOf(code.get(i).toUpperCase());
                if (target < 0) {
                    target = keys.indexOf(code.get(i));
                }
                for (int k = 0; k < target; k++) {
                    menuClick(9 + start + i);
                    Thread.sleep(125); // same slot: wait for the double-click guard
                }
            }
            menuClick(40);
        } else {
            int[] slots;
            if (style.equals("digits") && keys.size() == 10) {
                slots = new int[]{40, 12, 13, 14, 21, 22, 23, 30, 31, 32};
            } else {
                slots = new int[36];
                for (int i = 0; i < 36; i++) {
                    slots[i] = 9 + i;
                }
            }
            int previous = -1;
            for (String c : code) {
                int k = indexOf(keys, c);
                if (slots[k] == previous) {
                    Thread.sleep(125);
                }
                menuClick(slots[k]);
                previous = slots[k];
            }
        }
        if (openMenu() instanceof Menu) {
            player.closeInventory();
        }
    }

    private static int indexOf(List<String> keys, String c) {
        for (int i = 0; i < keys.size(); i++) {
            if (Text.normalize(keys.get(i)).equals(Text.normalize(c))) {
                return i;
            }
        }
        throw new IllegalStateException("key " + c + " not on the keypad " + keys);
    }

    private void simon(Puzzle pz) {
        ConfigurationSection s = pz.def().section();
        List<Block> buttons = game.blocks(pz.def().list("buttons"));
        int rounds = Math.max(1, s.getInt("rounds", 5));
        Random r = new Random(s.getInt("seed", 7));
        List<Integer> seq = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            seq.add(r.nextInt(buttons.size()));
        }
        click(pz.def().list("start").get(0));
        ticks(16L + 20);
        for (int round = 1; round <= rounds && pz.isActive(); round++) {
            for (int i = 0; i < round; i++) {
                click(buttons.get(seq.get(i)));
            }
            ticks(30L + 16L * (round + 1) + 20);
        }
    }

    private void frames(Puzzle pz) throws Exception {
        for (ConfigurationSection f : Yml.sections(pz.def().section(), "frames")) {
            String item = f.getString("item");
            if (item == null || !hasItem(item)) {
                continue;
            }
            Location at = game.center(f.getString("at"));
            Block target = game.block(f.getString("at"));
            ItemFrame frame = null;
            for (Entity e : at.getWorld().getNearbyEntities(at, 1.5, 1.5, 1.5)) {
                if (e instanceof ItemFrame fr && fr.getLocation().getBlock().equals(target)) {
                    frame = fr; // frames on neighbouring blocks are close too: match the exact block
                }
            }
            assertNotNull(frame, pz.id() + ": no frame at " + f.getString("at"));
            ItemStack inFrame = frame.getItem();
            if (inFrame != null && item.equals(Keys.itemId(inFrame))) {
                continue;
            }
            hold(item);
            clickEntity(frame);
        }
        emptyHand();
    }

    private void choice(Puzzle pz) throws Exception {
        String npc = pz.def().str("npc", null);
        if (npc != null) {
            Entity e = game.props().entity(npc);
            assertNotNull(e, pz.id() + ": npc " + npc + " not spawned");
            assertEquals(npc, PropManager.propId(e));
            clickEntity(e);
        } else {
            click(pz.def().list("at").get(0));
        }
        assertTrue(openMenu() instanceof Menu, pz.id() + ": choice menu didn't open");
        List<ConfigurationSection> options = Yml.sections(pz.def().section(), "options");
        int start = 9 + Math.max(0, (9 - Math.min(9, options.size())) / 2);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).getBoolean("correct", false)) {
                menuClick(options.size() <= 9 ? start + i : 9 + i);
                break;
            }
        }
    }

    private void memory(Puzzle pz) throws Exception {
        click(pz.def().list("at").get(0));
        Object board = openMenu();
        assertTrue(board instanceof Menu, pz.id() + ": memory menu didn't open");
        List<Material> cards = field(board, "cards");
        Map<Material, List<Integer>> pairs = new HashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            pairs.computeIfAbsent(cards.get(i), k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> pair : pairs.values()) {
            for (int card : pair) {
                int row = card / 6;
                int col = card % 6;
                menuClick(9 + row * 9 + 1 + col);
            }
        }
    }

    private void slide(Puzzle pz) throws Exception {
        click(pz.def().list("at").get(0));
        assertTrue(openMenu() instanceof Menu, pz.id() + ": slide menu didn't open");
        int[] board = field(pz, "board");
        List<Integer> moves = solveSlide(board.clone());
        assertNotNull(moves, "slide board unsolvable");
        int previous = -1;
        for (int cell : moves) {
            int slot = 9 + (cell / 3) * 9 + 3 + (cell % 3);
            if (slot == previous) {
                Thread.sleep(125);
            }
            menuClick(slot);
            previous = slot;
        }
    }

    /** BFS: the cells to click (the tile next to the hole) to reach 1..8,0. */
    private static List<Integer> solveSlide(int[] start) {
        String goal = "123456780";
        String s0 = Arrays.toString(start).replaceAll("[^0-9]", "");
        Map<String, String> prev = new HashMap<>();
        Map<String, Integer> moveTo = new HashMap<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        q.add(s0);
        prev.put(s0, null);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur.equals(goal)) {
                List<Integer> path = new ArrayList<>();
                String c = cur;
                while (prev.get(c) != null) {
                    path.add(0, moveTo.get(c));
                    c = prev.get(c);
                }
                return path;
            }
            int hole = cur.indexOf('0');
            int r = hole / 3;
            int col = hole % 3;
            int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dd : d) {
                int nr = r + dd[0];
                int nc = col + dd[1];
                if (nr < 0 || nc < 0 || nr > 2 || nc > 2) {
                    continue;
                }
                int cell = nr * 3 + nc;
                char[] ch = cur.toCharArray();
                ch[hole] = ch[cell];
                ch[cell] = '0';
                String next = new String(ch);
                if (!prev.containsKey(next)) {
                    prev.put(next, cur);
                    moveTo.put(next, cell);
                    q.add(next);
                }
            }
        }
        return null;
    }
}
