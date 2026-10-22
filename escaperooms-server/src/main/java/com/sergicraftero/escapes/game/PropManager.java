package com.sergicraftero.escapes.game;

import com.sergicraftero.escapes.action.Action;
import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.gui.RecipeMenu;
import com.sergicraftero.escapes.instance.PasteJob;
import com.sergicraftero.escapes.util.Cosmetic;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Yml;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Room props from the YAML {@code props:} list: holograms, UV-only writing, NPCs, frames, floating items,
 * search spots, workbenches, teleporters, hazards, chest/sign/lectern contents, particle emitters.
 * <p>
 * Common keys: {@code id}, {@code type}, {@code at}, {@code requires: [puzzles]} (appears when solved),
 * {@code until: puzzle} (disappears when solved), {@code manual: true} (only via the show/hide actions).
 */
public final class PropManager {

    private final Game game;
    private final Map<String, Prop> props = new LinkedHashMap<>();
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();

    PropManager(Game game) {
        this.game = game;
    }

    void setup() {
        int n = 0;
        for (ConfigurationSection s : game.room().props) {
            String id = s.getString("id", "prop" + (n++));
            props.put(id, new Prop(id, s));
        }
        refresh();
    }

    /** Re-evaluates which props should exist after puzzle progress. */
    public void refresh() {
        for (Prop p : props.values()) {
            boolean want = p.wanted();
            if (want && !p.spawned) {
                p.spawn();
            } else if (!want && p.spawned) {
                p.despawn();
            }
        }
    }

    public void spawn(String id) {
        Prop p = props.get(id);
        if (p != null) {
            p.forced = Boolean.TRUE;
            refresh();
        }
    }

    public void remove(String id) {
        Prop p = props.get(id);
        if (p != null) {
            p.forced = Boolean.FALSE;
            refresh();
        }
    }

    public Entity entity(String propId) {
        Prop p = props.get(propId);
        return p == null || p.entities.isEmpty() ? null : p.entities.get(0);
    }

    public static String propId(Entity e) {
        return e.getPersistentDataContainer().get(Keys.PROP, PersistentDataType.STRING);
    }

    // ───────────────────────── events ─────────────────────────

    boolean interact(Player player, Block b, boolean right, ItemStack hand) {
        for (Prop p : props.values()) {
            if (p.spawned && p.blocks.contains(b)) {
                return p.click(player, b, right, hand);
            }
        }
        return false;
    }

    boolean interactEntity(Player player, Entity e, boolean right, ItemStack hand) {
        String id = propId(e);
        Prop p = id == null ? null : props.get(id);
        if (p == null || !p.spawned) {
            return false;
        }
        return p.clickEntity(player, right);
    }

    void moved(Player player, Location to) {
        for (Prop p : props.values()) {
            if (p.spawned && p.region != null && p.region.contains(to.toVector())) {
                p.enter(player);
            }
        }
    }

    void tick() {
        for (Prop p : props.values()) {
            if (p.spawned) {
                p.tick();
            }
        }
    }

    void cleanup() {
        for (Prop p : props.values()) {
            p.despawn();
        }
    }

    // ───────────────────────── a prop ─────────────────────────

    private final class Prop {
        final String id;
        final ConfigurationSection s;
        final String type;
        final List<String> requires;
        final String until;
        final boolean manual;
        Boolean forced;
        boolean spawned;
        final List<Entity> entities = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();
        BoundingBox region;
        final Set<UUID> found = new HashSet<>();
        boolean foundOnce;
        final Map<UUID, Integer> talkIndex = new HashMap<>();
        int ticks;

        Prop(String id, ConfigurationSection s) {
            this.id = id;
            this.s = s;
            this.type = s.getString("type", "text").toLowerCase(Locale.ROOT);
            this.requires = Yml.strings(s, "requires");
            this.until = s.getString("until");
            this.manual = s.getBoolean("manual", false);
        }

        boolean wanted() {
            if (forced != null) {
                return forced;
            }
            if (manual) {
                return false;
            }
            for (String r : requires) {
                if (!game.isSolved(r)) {
                    return false;
                }
            }
            return until == null || !game.isSolved(until);
        }

        String at() {
            return s.getString("at", "@spawn");
        }

        Location loc() {
            Location l = game.center(at());
            l.add(s.getDouble("offset.x", 0), s.getDouble("offset.y", 0), s.getDouble("offset.z", 0));
            return l;
        }

        float yaw() {
            if (s.contains("yaw")) {
                return (float) s.getDouble("yaw");
            }
            String facing = s.getString("facing", com.sergicraftero.escapes.template.Refs.facing(at(), game.template()));
            if (facing == null) {
                return 0f;
            }
            return switch (facing) {
                case "north" -> 180f;
                case "east" -> -90f;
                case "west" -> 90f;
                default -> 0f;
            };
        }

        <T extends Entity> T tag(T e) {
            game.tagEntity(e);
            e.getPersistentDataContainer().set(Keys.PROP, PersistentDataType.STRING, id);
            entities.add(e);
            return e;
        }

        void spawn() {
            spawned = true;
            try {
                doSpawn();
            } catch (RuntimeException ex) {
                game.plugin().getLogger().log(java.util.logging.Level.WARNING, "[" + game.room().id + "] Prop " + id + " failed: " + ex, ex);
            }
        }

        private void doSpawn() {
            switch (type) {
                case "text", "hologram" -> {
                    Location l = loc();
                    l.setYaw(yaw());
                    tag(l.getWorld().spawn(l, TextDisplay.class, d -> styleText(d, s.getString("text", ""))));
                }
                case "uv-text", "uv" -> {
                    Location l = loc();
                    l.setYaw(yaw());
                    tag(l.getWorld().spawn(l, TextDisplay.class, d -> {
                        styleText(d, s.getString("text", ""));
                        Cosmetic.apply(() -> d.setVisibleByDefault(false));
                    }));
                }
                case "npc" -> spawnNpc();
                case "frame" -> {
                    Block b = game.block(at());
                    BlockFace face = BlockFace.valueOf(s.getString("facing",
                            String.valueOf(com.sergicraftero.escapes.template.Refs.facing(at(), game.template()))).toUpperCase(Locale.ROOT)
                            .replace("NULL", "SOUTH"));
                    Location l = b.getLocation();
                    java.util.function.Consumer<ItemFrame> setupFrame = f -> {
                        f.setFacingDirection(face, true);
                        String item = s.getString("item");
                        if (item != null) {
                            ItemStack it = game.item(item, 1);
                            if (it != null) {
                                f.setItem(it, false);
                            }
                        }
                        f.setRotation(org.bukkit.Rotation.values()[Math.floorMod(s.getInt("rotation", 0), 8)]);
                        f.setFixed(true);
                        f.setVisible(!s.getBoolean("invisible", false));
                    };
                    if (s.getBoolean("glow", false)) {
                        tag(l.getWorld().spawn(l, org.bukkit.entity.GlowItemFrame.class, setupFrame::accept));
                    } else {
                        tag(l.getWorld().spawn(l, ItemFrame.class, setupFrame::accept));
                    }
                }
                case "item", "item-display" -> {
                    Location l = loc();
                    l.setYaw(yaw());
                    ItemStack it = game.item(s.getString("item", "minecraft:paper"), 1);
                    tag(l.getWorld().spawn(l, ItemDisplay.class, d -> {
                        d.setItemStack(it);
                        float scale = (float) s.getDouble("scale", 0.6);
                        Cosmetic.apply(() -> d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                                new Vector3f(scale, scale, scale), new AxisAngle4f())));
                        Cosmetic.apply(() -> d.setBillboard(s.getBoolean("billboard", false) ? Display.Billboard.CENTER : Display.Billboard.FIXED));
                    }));
                }
                case "search" -> blocks.addAll(game.blocks(at()));
                case "workbench" -> blocks.addAll(game.blocks(at()));
                case "clue" -> blocks.addAll(game.blocks(at()));
                case "teleporter", "hazard" -> {
                    String r = s.getString("region", at());
                    region = game.box(r);
                    if (s.contains("at") && type.equals("teleporter") && !s.contains("region")) {
                        blocks.addAll(game.blocks(at()));
                        region = null;
                    }
                }
                case "container" -> {
                    for (Block b : game.blocks(at())) {
                        if (b.getState() instanceof Container c) {
                            // fill the snapshot and write it back: works the same on every server build
                            Inventory inv = c.getSnapshotInventory();
                            if (s.getBoolean("clear", true)) {
                                inv.clear();
                            }
                            List<String> items = Yml.strings(s, "items");
                            for (int i = 0; i < items.size(); i++) {
                                String[] parts = items.get(i).split("\\*");
                                int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                                ItemStack it = game.item(parts[0].trim(), amount);
                                if (it == null) {
                                    continue;
                                }
                                int slot = s.contains("slots") ? s.getIntegerList("slots").get(Math.min(i, s.getIntegerList("slots").size() - 1))
                                        : Math.min(inv.getSize() - 1, (i * 3 + 4) % inv.getSize());
                                if (inv.getItem(slot) != null) {
                                    inv.addItem(it);
                                } else {
                                    inv.setItem(slot, it);
                                }
                            }
                            c.update(true, false);
                        }
                    }
                }
                case "sign" -> {
                    for (Block b : game.blocks(at())) {
                        if (b.getState() instanceof Sign sign) {
                            List<String> lines = Yml.strings(s, "lines");
                            for (int i = 0; i < 4; i++) {
                                sign.getSide(Side.FRONT).line(i, i < lines.size() ? Msg.parse(lines.get(i)) : Component.empty());
                            }
                            sign.setWaxed(true);
                            sign.update(true, false);
                        }
                    }
                }
                case "book", "lectern" -> {
                    ItemStack book = game.item(s.getString("item", ""), 1);
                    if (book != null) {
                        for (Block b : game.blocks(at())) {
                            PasteJob.setLecternBook(b, book);
                        }
                    }
                }
                case "particles" -> {
                    // emitted in tick()
                }
                case "block" -> {
                    String data = s.getString("block", "minecraft:stone");
                    for (Block b : game.blocks(at())) {
                        b.setBlockData(org.bukkit.Bukkit.createBlockData(data), false);
                    }
                }
                default -> game.plugin().getLogger().warning("[" + game.room().id + "] Unknown prop type " + type);
            }
        }

        private void styleText(TextDisplay d, String text) {
            d.text(Msg.parse(text));
            Cosmetic.apply(() -> d.setBillboard(Display.Billboard.valueOf(s.getString("billboard", type.startsWith("uv") ? "FIXED" : "CENTER")
                    .toUpperCase(Locale.ROOT))));
            float scale = (float) s.getDouble("scale", 1.0);
            Cosmetic.apply(() -> d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f())));
            Cosmetic.apply(() -> d.setShadowed(s.getBoolean("shadow", false)));
            Cosmetic.apply(() -> d.setSeeThrough(false));
            Cosmetic.apply(() -> d.setLineWidth(s.getInt("width", 200)));
            if (s.contains("background")) {
                Cosmetic.apply(() -> d.setBackgroundColor(org.bukkit.Color.fromARGB((int) Long.parseLong(s.getString("background").replace("#", ""), 16))));
            } else if (type.startsWith("uv")) {
                Cosmetic.apply(() -> d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)));
            }
            Cosmetic.apply(() -> d.setBrightness(new Display.Brightness(15, 15)));
            Cosmetic.apply(() -> d.setAlignment(TextDisplay.TextAlignment.CENTER));
        }

        private void spawnNpc() {
            Location l = loc().add(0, -0.5, 0);
            l.setYaw(yaw());
            tag(l.getWorld().spawn(l, Villager.class, v -> {
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setCollidable(false);
                v.setGravity(false);
                v.setPersistent(false);
                Cosmetic.apply(() -> v.setRemoveWhenFarAway(false));
                v.customName(Msg.parse(s.getString("name", "NPC")));
                v.setCustomNameVisible(true);
                Villager.Profession prof = org.bukkit.Registry.VILLAGER_PROFESSION.get(
                        org.bukkit.NamespacedKey.minecraft(s.getString("profession", "none").toLowerCase(Locale.ROOT)));
                if (prof != null) {
                    v.setProfession(prof);
                }
                Villager.Type vt = org.bukkit.Registry.VILLAGER_TYPE.get(
                        org.bukkit.NamespacedKey.minecraft(s.getString("biome", "plains").toLowerCase(Locale.ROOT)));
                if (vt != null) {
                    v.setVillagerType(vt);
                }
                v.setVillagerLevel(5);
                if (s.getBoolean("baby", false)) {
                    v.setBaby();
                }
            }));
        }

        void despawn() {
            spawned = false;
            for (Entity e : entities) {
                e.remove();
            }
            entities.clear();
            blocks.clear();
            region = null;
        }

        boolean click(Player p, Block b, boolean right, ItemStack hand) {
            switch (type) {
                case "search" -> {
                    search(p, b);
                    return true;
                }
                case "workbench" -> {
                    new RecipeMenu(game, p, Yml.strings(s, "recipes"), s.getString("title")).open(p);
                    return true;
                }
                case "clue" -> {
                    // read-only: a message when inspecting a block (a painting, a carving...)
                    Yml.strings(s, "text").forEach(line -> p.sendMessage(Msg.parse(line, "player", p.getName())));
                    p.playSound(Actions.sound(s.getString("sound", "item.book.page_turn"), 0.8f, 1f), Sound.Emitter.self());
                    return true;
                }
                case "teleporter" -> {
                    teleport(p);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private void search(Player p, Block clicked) {
            boolean perPlayer = s.getBoolean("per-player", false);
            boolean already = perPlayer ? found.contains(p.getUniqueId()) : foundOnce;
            if (already) {
                String empty = s.getString("empty-message");
                if (empty == null) {
                    Msg.actionBar(p, "props.search-empty");
                } else if (!empty.isEmpty()) {
                    p.sendActionBar(Msg.parse(empty));
                }
                return;
            }
            String tool = s.getString("requires-item");
            if (tool != null && !Game.has(p, tool)) {
                String need = s.getString("need-message");
                if (need == null) {
                    Msg.actionBar(p, "props.search-need-tool");
                } else {
                    p.sendActionBar(Msg.parse(need));
                }
                p.playSound(Actions.sound("block.chest.locked", 0.8f, 1.2f), Sound.Emitter.self());
                return;
            }
            if (tool != null && s.getBoolean("consume-item", false)) {
                game.consume(p, tool);
            }
            found.add(p.getUniqueId());
            foundOnce = true;
            String item = s.getString("item");
            if (item != null) {
                game.giveItem(p, item, s.getInt("amount", 1));
            }
            for (String spec : Yml.strings(s, "items")) {
                String[] parts = spec.split("\\*");
                game.giveItem(p, parts[0].trim(), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1);
            }
            if (item == null && s.contains("items") && s.getString("message") == null) {
                game.broadcast("props.search-found-many", "player", p.getName());
            }
            String msg = s.getString("message");
            if (msg != null) {
                game.broadcast(Msg.parse(msg, "player", p.getName()));
            } else if (item != null) {
                ItemStack it = game.item(item, 1);
                game.broadcast("props.search-found", "player", p.getName(),
                        "item", it != null && it.getItemMeta().hasDisplayName() ? it.getItemMeta().displayName() : Component.text(item));
            }
            List<Action> actions = Actions.parseList(s, "actions", game.plugin().getLogger(), game.room().id + "/" + id);
            game.runActions(actions, p);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, clicked.getLocation().add(0.5, 1, 0.5), 10, 0.3, 0.3, 0.3);
        }

        boolean clickEntity(Player p, boolean right) {
            if (!type.equals("npc")) {
                return true;
            }
            List<String> talk = Yml.strings(s, "talk");
            if (game.props().solvedTalk(s, p)) {
                return true;
            }
            if (talk.isEmpty()) {
                return true;
            }
            int i = talkIndex.getOrDefault(p.getUniqueId(), 0);
            String line = talk.get(i % talk.size());
            talkIndex.put(p.getUniqueId(), i + 1);
            p.sendMessage(Msg.get("props.npc-says", "npc", Msg.parse(s.getString("name", "NPC")), "text", Msg.parse(line, "player", p.getName())));
            p.playSound(Actions.sound(s.getString("voice", "entity.villager.ambient"), 0.8f, 1f), Sound.Emitter.self());
            return true;
        }

        void enter(Player p) {
            long now = System.currentTimeMillis();
            Long cd = teleportCooldown.get(p.getUniqueId());
            if (cd != null && now < cd) {
                return;
            }
            teleportCooldown.put(p.getUniqueId(), now + 1500);
            if (type.equals("hazard")) {
                String to = s.getString("to");
                p.teleport(to == null ? (game.checkpoint() == null ? game.standLocation(game.room().spawn) : game.checkpoint())
                        : game.standLocation(to));
                p.setFallDistance(0);
                String msg = s.getString("message");
                if (msg == null) {
                    Msg.send(p, "props.hazard");
                } else {
                    p.sendMessage(Msg.parse(msg));
                }
                p.playSound(Actions.sound(s.getString("sound", "entity.player.hurt"), 0.8f, 1f), Sound.Emitter.self());
            } else if (type.equals("teleporter")) {
                teleport(p);
            }
        }

        private void teleport(Player p) {
            String to = s.getString("to", "@spawn");
            boolean all = s.getBoolean("whole-team", false);
            Location dest = game.standLocation(to);
            if (all) {
                game.players().forEach(pl -> pl.teleport(dest));
            } else {
                p.teleport(dest);
            }
            String msg = s.getString("message");
            if (msg != null) {
                p.sendMessage(Msg.parse(msg));
            }
            p.playSound(Actions.sound(s.getString("sound", "entity.enderman.teleport"), 0.6f, 1.2f), Sound.Emitter.self());
        }

        void tick() {
            ticks++;
            if (type.startsWith("uv")) {
                double radius = s.getDouble("radius", 5);
                for (Entity e : entities) {
                    for (Player p : game.players()) {
                        boolean holding = Keys.hasTag(p.getInventory().getItemInMainHand(), "uv")
                                || Keys.hasTag(p.getInventory().getItemInOffHand(), "uv");
                        boolean near = p.getLocation().distanceSquared(e.getLocation()) <= radius * radius;
                        if (holding && near) {
                            if (!p.canSee(e)) {
                                p.showEntity(game.plugin(), e);
                                p.playSound(Actions.sound("block.amethyst_block.resonate", 0.6f, 1.6f), Sound.Emitter.self());
                            }
                            if (ticks % 4 == 0) {
                                p.spawnParticle(Particle.DUST, e.getLocation(), 3, 0.4, 0.2, 0.4, 0,
                                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0x9B30FF), 0.8f));
                            }
                        } else if (p.canSee(e)) {
                            p.hideEntity(game.plugin(), e);
                        }
                    }
                }
            } else if (type.equals("particles")) {
                long every = Math.max(1, Yml.ticks(s.get("interval"), 10) / 5);
                if (ticks % every == 0) {
                    for (Block b : game.blocks(at())) {
                        Actions.spawnParticle(Actions.particleType(s.getString("particle", "END_ROD")),
                                b.getLocation().add(0.5, s.getDouble("height", 0.5), 0.5), s.getInt("count", 3),
                                s.getDouble("spread", 0.3), s.getDouble("speed", 0.01), s.getString("color", "#FFFFFF"),
                                (float) s.getDouble("size", 1.0));
                    }
                }
            } else if (type.equals("item") && s.getBoolean("spin", false)) {
                for (Entity e : entities) {
                    if (e instanceof ItemDisplay d) {
                        Location l = d.getLocation();
                        l.setYaw(l.getYaw() + 9f);
                        d.setRotation(l.getYaw(), 0);
                    }
                }
            }
        }
    }

    /**
     * An NPC with {@code after-solved: {puzzle: id, talk: [...]}} switches its lines once that puzzle is solved.
     * Returns true when the alternative lines were used.
     */
    boolean solvedTalk(ConfigurationSection s, Player p) {
        ConfigurationSection after = s.getConfigurationSection("after-solved");
        if (after == null || !game.isSolved(after.getString("puzzle", ""))) {
            return false;
        }
        List<String> lines = Yml.strings(after, "talk");
        if (lines.isEmpty()) {
            return false;
        }
        String line = lines.get((int) (Math.random() * lines.size()));
        p.sendMessage(Msg.get("props.npc-says", "npc", Msg.parse(s.getString("name", "NPC")), "text", Msg.parse(line)));
        return true;
    }

    public boolean has(String id) {
        return props.containsKey(id);
    }

    public static Material material(String s) {
        Material m = Material.matchMaterial(s);
        return m == null ? Material.STONE : m;
    }
}
