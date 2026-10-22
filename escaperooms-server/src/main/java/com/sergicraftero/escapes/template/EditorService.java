package com.sergicraftero.escapes.template;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.instance.PasteJob;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-game map editor. {@code /eradmin editor <template>} pastes a map in a private spot of the instance world,
 * the admin edits it in creative, moves markers with {@code /eradmin marker ...} and saves it back.
 * Also captures brand-new maps built anywhere ({@code /eradmin capture}).
 */
public final class EditorService {

    private static final int EDITOR_Z = -4096;

    public static final class Session {
        final String id;
        final World world;
        final int ox;
        final int oy;
        final int oz;
        Template template;
        final List<Entity> labels = new ArrayList<>();

        Session(String id, World world, int ox, int oy, int oz, Template template) {
            this.id = id;
            this.world = world;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.template = template;
        }
    }

    private final EscapePlugin plugin;
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<UUID, String> editing = new HashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private int nextSlot;

    public EditorService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public Session session(Player p) {
        String id = editing.get(p.getUniqueId());
        return id == null ? null : sessions.get(id);
    }

    public void open(Player p, String templateId) {
        Session s = sessions.get(templateId);
        if (s == null) {
            TemplateStore.Loaded loaded = plugin.templates().get(templateId);
            if (loaded == null) {
                Msg.send(p, "admin.no-template", "id", templateId);
                return;
            }
            World w = plugin.instances().world();
            int ox = (nextSlot++) * 512;
            s = new Session(templateId, w, ox, 64, EDITOR_Z, loaded.template());
            sessions.put(templateId, s);
            PasteJob job = new PasteJob(loaded.template(), loaded.palette(), w, ox, 64, EDITOR_Z, false,
                    spec -> plugin.items().resolve(null, spec), null);
            while (!job.step(1_000_000)) {
                // paste at once: editing is an admin action
            }
            showMarkers(s);
        }
        editing.put(p.getUniqueId(), templateId);
        Template.Marker spawn = s.template.marker("spawn");
        Location l = spawn == null ? new Location(s.world, s.ox + 2, s.oy + 2, s.oz + 2)
                : new Location(s.world, s.ox + spawn.x() + 0.5, s.oy + spawn.y(), s.oz + spawn.z() + 0.5);
        p.teleport(l);
        p.setGameMode(GameMode.CREATIVE);
        Msg.send(p, "admin.editor-open", "id", templateId);
    }

    private void showMarkers(Session s) {
        s.labels.forEach(Entity::remove);
        s.labels.clear();
        for (Map.Entry<String, Template.Marker> e : s.template.markers().entrySet()) {
            Template.Marker m = e.getValue();
            Location l = new Location(s.world, s.ox + (m.x() + m.x2()) / 2.0 + 0.5, s.oy + m.y() + 0.6,
                    s.oz + (m.z() + m.z2()) / 2.0 + 0.5);
            TextDisplay d = s.world.spawn(l, TextDisplay.class, t -> {
                t.text(Msg.parse("<yellow>" + Msg.escape(e.getKey()) + (m.isBox() ? " <gray>[caja]" : "")
                        + (m.facing() != null ? " <gray>→" + m.facing() : "")));
                t.setBillboard(Display.Billboard.CENTER);
                t.setSeeThrough(true);
                t.setPersistent(false);
                t.getPersistentDataContainer().set(Keys.INSTANCE, PersistentDataType.STRING, "editor");
            });
            s.labels.add(d);
        }
    }

    public void setMarker(Player p, String name, String facing, boolean useTarget) {
        Session s = session(p);
        if (s == null) {
            Msg.send(p, "admin.not-editing");
            return;
        }
        Block b = useTarget ? p.getTargetBlockExact(6) : p.getLocation().getBlock();
        if (b == null) {
            Msg.send(p, "admin.look-at-block");
            return;
        }
        int x = b.getX() - s.ox;
        int y = b.getY() - s.oy;
        int z = b.getZ() - s.oz;
        if (!s.template.inside(x, y, z)) {
            Msg.send(p, "admin.outside-map");
            return;
        }
        s.template.mark(name, x, y, z, facing);
        showMarkers(s);
        Msg.send(p, "admin.marker-set", "name", name, "pos", x + "," + y + "," + z);
    }

    public void setBoxMarker(Player p, String name) {
        Session s = session(p);
        Location[] sel = selections.get(p.getUniqueId());
        if (s == null || sel == null || sel[0] == null || sel[1] == null) {
            Msg.send(p, "admin.need-selection");
            return;
        }
        s.template.markBox(name, sel[0].getBlockX() - s.ox, sel[0].getBlockY() - s.oy, sel[0].getBlockZ() - s.oz,
                sel[1].getBlockX() - s.ox, sel[1].getBlockY() - s.oy, sel[1].getBlockZ() - s.oz);
        showMarkers(s);
        Msg.send(p, "admin.marker-set", "name", name, "pos", "caja");
    }

    public void deleteMarker(Player p, String name) {
        Session s = session(p);
        if (s == null) {
            Msg.send(p, "admin.not-editing");
            return;
        }
        s.template.markers().remove(name);
        showMarkers(s);
        Msg.send(p, "admin.marker-deleted", "name", name);
    }

    public List<String> markers(Player p) {
        Session s = session(p);
        return s == null ? List.of() : new ArrayList<>(s.template.markers().keySet());
    }

    public void select(Player p, int index) {
        Block b = p.getTargetBlockExact(8);
        Location l = b == null ? p.getLocation() : b.getLocation();
        selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2])[index] = l;
        Msg.send(p, "admin.pos-set", "n", index + 1, "pos", l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
    }

    /** Saves the edited blocks (and markers) back to the template file. */
    public void save(Player p) {
        Session s = session(p);
        if (s == null) {
            Msg.send(p, "admin.not-editing");
            return;
        }
        Template t = capture(s.world, s.ox, s.oy, s.oz, s.template.sizeX(), s.template.sizeY(), s.template.sizeZ());
        t.markers().putAll(s.template.markers());
        try {
            plugin.templates().replace(s.id, t);
            s.template = t;
            plugin.instances().invalidate(s.id);
            Msg.send(p, "admin.editor-saved", "id", s.id);
        } catch (IOException e) {
            Msg.send(p, "admin.error", "error", e.getMessage());
        }
    }

    public void close(Player p) {
        String id = editing.remove(p.getUniqueId());
        if (id != null && !editing.containsValue(id)) {
            Session s = sessions.remove(id);
            if (s != null) {
                s.labels.forEach(Entity::remove);
            }
        }
        plugin.lobby().send(p);
    }

    /** Captures a region selected with pos1/pos2 as a new template (markers: just the spawn at pos1). */
    public void captureSelection(Player p, String id) {
        Location[] sel = selections.get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null || sel[0].getWorld() != sel[1].getWorld()) {
            Msg.send(p, "admin.need-selection");
            return;
        }
        int x1 = Math.min(sel[0].getBlockX(), sel[1].getBlockX());
        int y1 = Math.min(sel[0].getBlockY(), sel[1].getBlockY());
        int z1 = Math.min(sel[0].getBlockZ(), sel[1].getBlockZ());
        int x2 = Math.max(sel[0].getBlockX(), sel[1].getBlockX());
        int y2 = Math.max(sel[0].getBlockY(), sel[1].getBlockY());
        int z2 = Math.max(sel[0].getBlockZ(), sel[1].getBlockZ());
        if ((long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1) > 8_000_000) {
            Msg.send(p, "admin.too-big");
            return;
        }
        Template t = capture(sel[0].getWorld(), x1, y1, z1, x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1);
        Location pl = p.getLocation();
        if (t.inside(pl.getBlockX() - x1, pl.getBlockY() - y1, pl.getBlockZ() - z1)) {
            t.mark("spawn", pl.getBlockX() - x1, pl.getBlockY() - y1, pl.getBlockZ() - z1);
        }
        try {
            plugin.templates().replace(id, t);
            Msg.send(p, "admin.captured", "id", id, "size", t.sizeX() + "x" + t.sizeY() + "x" + t.sizeZ());
        } catch (IOException e) {
            Msg.send(p, "admin.error", "error", e.getMessage());
        }
    }

    public static Template capture(World w, int ox, int oy, int oz, int sx, int sy, int sz) {
        Template t = new Template(sx, sy, sz);
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Block b = w.getBlockAt(ox + x, oy + y, oz + z);
                    String data = b.getBlockData().getAsString();
                    Integer id = ids.get(data);
                    if (id == null) {
                        id = t.paletteId(data);
                        ids.put(data, id);
                    }
                    t.setId(x, y, z, id);
                    BlockState st = b.getState(false);
                    if (st instanceof Sign || st instanceof Container || st instanceof Lectern) {
                        Map<String, Object> tile = tileOf(b.getState());
                        if (tile != null) {
                            t.tile(x, y, z, tile);
                        }
                    }
                }
            }
        }
        return t;
    }

    private static Map<String, Object> tileOf(BlockState st) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (st instanceof Sign sign) {
            data.put("type", "sign");
            List<String> lines = new ArrayList<>();
            List<String> back = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                lines.add(MiniMessage.miniMessage().serialize(sign.getSide(Side.FRONT).line(i)));
                back.add(MiniMessage.miniMessage().serialize(sign.getSide(Side.BACK).line(i)));
            }
            data.put("lines", lines);
            if (back.stream().anyMatch(s -> !s.isEmpty())) {
                data.put("back", back);
            }
            if (sign.getSide(Side.FRONT).isGlowingText()) {
                data.put("glow", true);
            }
            return data;
        }
        if (st instanceof Lectern lectern) {
            ItemStack book = lectern.getInventory().getItem(0);
            if (book == null || book.getType().isAir()) {
                return null;
            }
            data.put("type", "lectern");
            data.put("bytes", Base64.getEncoder().encodeToString(book.serializeAsBytes()));
            return data;
        }
        if (st instanceof Container c) {
            Inventory inv = c.getInventory();
            List<String> encoded = new ArrayList<>();
            boolean any = false;
            for (ItemStack it : inv.getContents()) {
                if (it == null || it.getType().isAir()) {
                    encoded.add("");
                } else {
                    encoded.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
                    any = true;
                }
            }
            if (!any) {
                return null;
            }
            data.put("type", "container");
            data.put("bytes", encoded);
            return data;
        }
        return null;
    }
}
