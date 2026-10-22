package com.sergicraftero.escapes.lobby;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.instance.PasteJob;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.profile.Storage;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import com.sergicraftero.escapes.template.TemplateStore;
import com.sergicraftero.escapes.util.Cosmetic;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Sidebar;
import com.sergicraftero.escapes.util.Text;
import com.sergicraftero.escapes.util.Yml;
import com.sergicraftero.escapes.world.VoidGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The hub: its world, the generated map, spawn, NPCs, leaderboard holograms, wing portals, parkour,
 * sidebar and tab list. Configured in {@code lobby.yml}.
 */
public final class LobbyService {

    private final EscapePlugin plugin;
    private YamlConfiguration yml;
    private File file;
    private World world;
    private int ox;
    private int oy;
    private int oz;
    private Template template;
    private final Map<String, Entity> npcs = new HashMap<>();
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Map<UUID, Long> portalCooldown = new HashMap<>();
    private final Map<UUID, Long> parkourStart = new HashMap<>();
    private final Map<UUID, Location> parkourCheckpoint = new HashMap<>();
    private boolean ready;

    public LobbyService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration yml, File file) {
        this.yml = yml;
        this.file = file;
    }

    public void start() {
        String name = yml.getString("world", "er_lobby");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(yml.getLong("time", 6000));
        world.setStorm(false);
        List<Integer> origin = yml.getIntegerList("origin");
        ox = origin.size() == 3 ? origin.get(0) : -60;
        oy = origin.size() == 3 ? origin.get(1) : 64;
        oz = origin.size() == 3 ? origin.get(2) : -60;
        TemplateStore.Loaded loaded = plugin.templates().get(yml.getString("template", "lobby"));
        if (loaded == null) {
            plugin.getLogger().severe("Lobby template missing; players will spawn in the void world.");
            ready = true;
            return;
        }
        template = loaded.template();
        world.setSpawnLocation(spawn());
        long version = loaded.template().volume() + loaded.template().palette().size() * 31L + loaded.template().markers().size();
        boolean paste = yml.getLong("pasted-signature", -1) != version || yml.getBoolean("force-paste", false);
        if (paste) {
            plugin.getLogger().info("Building the lobby map...");
            PasteJob job = new PasteJob(template, loaded.palette(), world, ox, oy, oz, false,
                    spec -> plugin.items().resolve(null, spec), null);
            // the lobby is pasted at once on start (before players can join)
            while (!job.step(1_000_000)) {
                // keep going
            }
            yml.set("pasted-signature", version);
            yml.set("force-paste", false);
            save();
        }
        spawnEntities();
        ready = true;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateHolograms, 40L, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateSidebars, 20L, 40L);
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save lobby.yml: " + e.getMessage());
        }
    }

    public World world() {
        return world;
    }

    public boolean ready() {
        return ready;
    }

    public boolean inLobby(Player p) {
        return world != null && p.getWorld() == world;
    }

    public Location at(String ref) {
        int[] p = Refs.point(ref, template);
        Location l = new Location(world, ox + p[0] + 0.5, oy + p[1], oz + p[2] + 0.5);
        String facing = Refs.facing(ref, template);
        if (facing != null) {
            l.setYaw(switch (facing) {
                case "north" -> 180f;
                case "east" -> -90f;
                case "west" -> 90f;
                default -> 0f;
            });
        }
        return l;
    }

    public BoundingBox box(String ref) {
        Template.Marker m = Refs.box(ref, template);
        if (m == null) {
            return null;
        }
        return new BoundingBox(ox + m.x(), oy + m.y(), oz + m.z(), ox + m.x2() + 1, oy + m.y2() + 1, oz + m.z2() + 1);
    }

    public Location spawn() {
        if (template == null) {
            return world.getSpawnLocation();
        }
        return at(yml.getString("spawn", "@spawn"));
    }

    // ───────────────────────── players ─────────────────────────

    /** Puts a player in the lobby: teleport, reset state, hotbar, sidebar. */
    public void send(Player p) {
        p.closeInventory();
        p.teleport(spawn());
        p.setGameMode(GameMode.ADVENTURE);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setLevel(0);
        p.setExp(0);
        p.resetPlayerTime();
        p.resetPlayerWeather();
        p.setAllowFlight(p.hasPermission("escaperooms.fly"));
        plugin.hotbar().giveLobby(p);
        Sidebar sb = new Sidebar(p, Msg.get("lobby.sidebar-title"));
        sidebars.put(p.getUniqueId(), sb);
        refresh(p);
        parkourStart.remove(p.getUniqueId());
        updateVisibility(p);
        p.sendPlayerListHeaderAndFooter(Msg.get("lobby.tab-header"), Msg.get("lobby.tab-footer",
                "online", Bukkit.getOnlinePlayers().size()));
    }

    public void quit(Player p) {
        sidebars.remove(p.getUniqueId());
        portalCooldown.remove(p.getUniqueId());
        parkourStart.remove(p.getUniqueId());
        parkourCheckpoint.remove(p.getUniqueId());
    }

    /** Re-draws a player's lobby sidebar now (after rewards, purchases...). */
    public void refresh(Player p) {
        Sidebar sb = sidebars.get(p.getUniqueId());
        if (sb == null || !inLobby(p)) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        int level = plugin.progress().level(prof.xp);
        List<Component> lines = new ArrayList<>();
        for (String line : Msg.rawList("lobby.sidebar")) {
            lines.add(Msg.parse(line, "level", level, "rank", Msg.parse(plugin.progress().rankName(level)),
                    "progress", Text.bar(plugin.progress().levelProgress(prof.xp), 10, "<green>|", "<dark_gray>|"),
                    "keys", Text.number(prof.keys), "gems", Text.number(prof.gems), "stars", prof.totalStars(),
                    "rooms", prof.roomsCompleted(), "total", plugin.rooms().all().size(),
                    "online", Bukkit.getOnlinePlayers().size(), "server", plugin.getConfig().getString("server-name", "EscapeRooms")));
        }
        sb.lines(lines);
    }

    private void updateSidebars() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    public void updateVisibility(Player viewer) {
        boolean hide = plugin.profiles().get(viewer).setting("hide-players", false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == viewer) {
                continue;
            }
            if (hide && inLobby(viewer)) {
                viewer.hidePlayer(plugin, other);
            } else {
                viewer.showPlayer(plugin, other);
            }
        }
    }

    // ───────────────────────── NPCs & holograms ─────────────────────────

    private void spawnEntities() {
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(Keys.LOBBY, PersistentDataType.STRING)) {
                e.remove();
            }
        }
        npcs.clear();
        holograms.clear();
        for (ConfigurationSection s : Yml.sections(yml, "npcs")) {
            String id = s.getString("id");
            String at = s.getString("at");
            if (id == null || at == null || !Refs.exists(at, template)) {
                plugin.getLogger().warning("Lobby NPC " + id + " has no valid position");
                continue;
            }
            Location l = at(at);
            Villager v = world.spawn(l, Villager.class, vil -> {
                vil.setAI(false);
                vil.setInvulnerable(true);
                vil.setSilent(true);
                vil.setCollidable(false);
                vil.setGravity(false);
                vil.setPersistent(false);
                vil.customName(Msg.parse(s.getString("name", id)));
                vil.setCustomNameVisible(true);
                Villager.Profession prof = org.bukkit.Registry.VILLAGER_PROFESSION.get(
                        org.bukkit.NamespacedKey.minecraft(s.getString("profession", "none").toLowerCase(Locale.ROOT)));
                if (prof != null) {
                    vil.setProfession(prof);
                }
                Villager.Type type = org.bukkit.Registry.VILLAGER_TYPE.get(
                        org.bukkit.NamespacedKey.minecraft(s.getString("biome", "plains").toLowerCase(Locale.ROOT)));
                if (type != null) {
                    vil.setVillagerType(type);
                }
                vil.setVillagerLevel(5);
                vil.getPersistentDataContainer().set(Keys.LOBBY, PersistentDataType.STRING, "npc:" + id);
            });
            npcs.put(id, v);
            // a floating subtitle above the name
            String sub = s.getString("subtitle");
            if (sub != null) {
                Location hl = l.clone().add(0, 2.55, 0);
                TextDisplay d = world.spawn(hl, TextDisplay.class, t -> {
                    t.text(Msg.parse(sub));
                    Cosmetic.apply(() -> t.setBillboard(Display.Billboard.CENTER));
                    t.setPersistent(false);
                    t.getPersistentDataContainer().set(Keys.LOBBY, PersistentDataType.STRING, "label:" + id);
                });
                holograms.put("label:" + id, d);
            }
        }
        for (ConfigurationSection s : Yml.sections(yml, "holograms")) {
            String id = s.getString("id");
            String at = s.getString("at");
            if (id == null || at == null || !Refs.exists(at, template)) {
                continue;
            }
            Location l = at(at).add(0, s.getDouble("height", 0), 0);
            TextDisplay d = world.spawn(l, TextDisplay.class, t -> {
                Cosmetic.apply(() -> t.setBillboard(Display.Billboard.valueOf(s.getString("billboard", "CENTER").toUpperCase(Locale.ROOT))));
                t.setPersistent(false);
                Cosmetic.apply(() -> t.setLineWidth(s.getInt("width", 220)));
                Cosmetic.apply(() -> t.setShadowed(true));
                t.getPersistentDataContainer().set(Keys.LOBBY, PersistentDataType.STRING, "holo:" + id);
            });
            holograms.put(id, d);
        }
        updateHolograms();
    }

    private void updateHolograms() {
        for (ConfigurationSection s : Yml.sections(yml, "holograms")) {
            TextDisplay d = holograms.get(s.getString("id", ""));
            if (d == null || !d.isValid()) {
                continue;
            }
            String type = s.getString("type", "text");
            Component text;
            switch (type) {
                case "levels" -> {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Msg.parse(s.getString("title", Msg.raw("lobby.top-levels-title"))));
                    int i = 1;
                    for (Storage.LevelEntry e : plugin.storage().topLevels()) {
                        lines.add(Msg.get("lobby.top-levels-line", "pos", i++, "player", e.name(),
                                "level", plugin.progress().level(e.xp())));
                    }
                    if (i == 1) {
                        lines.add(Msg.get("lobby.top-empty"));
                    }
                    text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
                }
                case "room" -> {
                    RoomDef room = plugin.rooms().get(s.getString("room"));
                    List<Component> lines = new ArrayList<>();
                    lines.add(Msg.get("lobby.top-room-title", "room", room == null ? Component.text("?") : Msg.parse(room.name)));
                    int i = 1;
                    if (room != null) {
                        for (Storage.Run r : s.getBoolean("weekly", false) ? plugin.storage().weekly(room.id) : plugin.storage().best(room.id)) {
                            lines.add(Msg.get("lobby.top-room-line", "pos", i++, "players", Msg.escape(r.players()),
                                    "time", Text.time(r.timeMs()), "stars", Text.stars(r.stars(), 3)));
                            if (i > s.getInt("lines", 5)) {
                                break;
                            }
                        }
                    }
                    if (i == 1) {
                        lines.add(Msg.get("lobby.top-empty"));
                    }
                    text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
                }
                default -> text = Msg.parse(String.join("\n", Yml.strings(s, "lines")),
                        "server", plugin.getConfig().getString("server-name", "EscapeRooms"),
                        "online", Bukkit.getOnlinePlayers().size());
            }
            d.text(text);
        }
        plugin.storage().refreshAllAsync(plugin.rooms().ids());
    }

    /** NPC id of a lobby villager, or null. */
    public String npcId(Entity e) {
        String tag = e.getPersistentDataContainer().get(Keys.LOBBY, PersistentDataType.STRING);
        return tag != null && tag.startsWith("npc:") ? tag.substring(4) : null;
    }

    public ConfigurationSection npc(String id) {
        for (ConfigurationSection s : Yml.sections(yml, "npcs")) {
            if (id.equals(s.getString("id"))) {
                return s;
            }
        }
        return null;
    }

    // ───────────────────────── movement: portals, parkour, void ─────────────────────────

    public void moved(Player p, Location to) {
        if (template == null) {
            return;
        }
        if (to.getY() < oy + yml.getInt("void-y", -10)) {
            Location cp = parkourCheckpoint.get(p.getUniqueId());
            p.teleport(cp != null ? cp : spawn());
            p.setFallDistance(0);
            return;
        }
        long now = System.currentTimeMillis();
        for (ConfigurationSection s : Yml.sections(yml, "portals")) {
            BoundingBox box = box(s.getString("region", ""));
            if (box == null || !box.contains(to.toVector())) {
                continue;
            }
            Long cd = portalCooldown.get(p.getUniqueId());
            if (cd != null && now < cd) {
                return;
            }
            portalCooldown.put(p.getUniqueId(), now + 2500);
            // push back out so closing the menu doesn't re-trigger it
            p.setVelocity(p.getLocation().getDirection().multiply(-0.6).setY(0.25));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_PORTAL_TRIGGER, 0.3f, 1.8f);
            plugin.ui().action(p, s.getString("action", "wing:" + s.getString("id")));
            return;
        }
        parkour(p, to, now);
    }

    private void parkour(Player p, Location to, long now) {
        ConfigurationSection pk = yml.getConfigurationSection("parkour");
        if (pk == null) {
            return;
        }
        BoundingBox start = box(pk.getString("start", ""));
        BoundingBox end = box(pk.getString("end", ""));
        if (start != null && start.contains(to.toVector()) && !parkourStart.containsKey(p.getUniqueId())) {
            parkourStart.put(p.getUniqueId(), now);
            parkourCheckpoint.put(p.getUniqueId(), at(pk.getString("start-spawn", pk.getString("start", "@spawn"))));
            Msg.send(p, "parkour.start");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
        if (!parkourStart.containsKey(p.getUniqueId())) {
            return;
        }
        for (String cpRef : Yml.strings(pk, "checkpoints")) {
            for (int[] pos : Refs.points(cpRef, template)) {
                if (to.getBlockX() == ox + pos[0] && to.getBlockY() == oy + pos[1] && to.getBlockZ() == oz + pos[2]) {
                    Location cp = new Location(world, ox + pos[0] + 0.5, oy + pos[1], oz + pos[2] + 0.5, to.getYaw(), 0);
                    Location prev = parkourCheckpoint.put(p.getUniqueId(), cp);
                    if (prev == null || prev.distanceSquared(cp) > 1) {
                        Msg.actionBar(p, "parkour.checkpoint");
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
                    }
                }
            }
        }
        if (end != null && end.contains(to.toVector())) {
            long time = now - parkourStart.remove(p.getUniqueId());
            parkourCheckpoint.remove(p.getUniqueId());
            Msg.send(p, "parkour.finish", "time", Text.time(time) + "." + (time % 1000) / 100);
            PlayerProfile prof = plugin.profiles().get(p);
            long best = prof.stat("parkour_best_ms");
            if (best == 0 || time < best) {
                prof.stats.put("parkour_best_ms", time);
                Msg.send(p, "parkour.record");
            }
            long day = plugin.progress().today();
            if (prof.stat("parkour_reward_day") != day) {
                prof.stats.put("parkour_reward_day", day);
                long keys = pk.getLong("reward-keys", 50);
                plugin.progress().addKeys(p, keys);
                Msg.send(p, "parkour.reward", "keys", keys);
            }
            prof.addStat("parkours", 1);
            plugin.progress().checkAchievements(p);
        }
    }
}
