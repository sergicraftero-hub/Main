package com.sergicraftero.escapes.cosmetic;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Purely cosmetic extras (never gameplay): chat titles, lobby trails, lobby hats and victory effects.
 * Defined in {@code cosmetics.yml}; owned through purchase (keys/gems), achievements or permissions
 * ({@code escaperooms.cosmetic.<type>.<id>}).
 */
public final class CosmeticService {

    public static final List<String> TYPES = List.of("titles", "trails", "hats", "victory");

    private final EscapePlugin plugin;
    private YamlConfiguration yml;
    private final Map<UUID, Location> lastTrail = new HashMap<>();

    public CosmeticService(EscapePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::trails, 4L, 4L);
    }

    public void load(YamlConfiguration yml) {
        this.yml = yml;
    }

    public ConfigurationSection section(String type) {
        return yml.getConfigurationSection(type);
    }

    public ConfigurationSection get(String type, String id) {
        return yml.getConfigurationSection(type + "." + id);
    }

    public List<String> ids(String type) {
        ConfigurationSection s = section(type);
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean owns(Player p, String type, String id) {
        ConfigurationSection c = get(type, id);
        if (c == null) {
            return false;
        }
        if (c.getBoolean("default", false)) {
            return true;
        }
        if (plugin.profiles().get(p).ownedCosmetics.contains(type + ":" + id)) {
            return true;
        }
        String perm = c.getString("permission", "escaperooms.cosmetic." + type + "." + id);
        return p.hasPermission(perm) || p.hasPermission("escaperooms.cosmetic." + type + ".*");
    }

    public String equipped(Player p, String type) {
        String id = plugin.profiles().get(p).equipped.get(type);
        return id != null && get(type, id) != null && owns(p, type, id) ? id : null;
    }

    public void equip(Player p, String type, String id) {
        PlayerProfile prof = plugin.profiles().get(p);
        if (id == null) {
            prof.equipped.remove(type);
        } else {
            prof.equipped.put(type, id);
        }
        prof.dirty = true;
        if (type.equals("hats")) {
            applyHat(p);
        }
    }

    // ───────────────────────── effects ─────────────────────────

    /** Chat prefix from the equipped title ("" when none). */
    public String title(Player p) {
        String id = equipped(p, "titles");
        return id == null ? "" : get("titles", id).getString("display", "");
    }

    public void applyHat(Player p) {
        if (!plugin.lobby().inLobby(p)) {
            return;
        }
        String id = equipped(p, "hats");
        if (id == null) {
            p.getInventory().setHelmet(null);
            return;
        }
        ConfigurationSection item = get("hats", id).getConfigurationSection("item");
        p.getInventory().setHelmet(ItemSpec.parse("hat_" + id, item).create());
    }

    private void trails() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.lobby().inLobby(p)) {
                continue;
            }
            String id = equipped(p, "trails");
            if (id == null) {
                continue;
            }
            Location now = p.getLocation();
            Location before = lastTrail.put(p.getUniqueId(), now);
            if (before != null && before.getWorld() == now.getWorld() && before.distanceSquared(now) < 0.01) {
                continue; // standing still: no trail
            }
            ConfigurationSection c = get("trails", id);
            Actions.spawnParticle(Actions.particleType(c.getString("particle", "END_ROD")), now.clone().add(0, 0.15, 0),
                    c.getInt("count", 3), c.getDouble("spread", 0.15), c.getDouble("speed", 0.01),
                    c.getString("color", "#FFFFFF"), (float) c.getDouble("size", 1.0));
        }
    }

    public void victory(Player p) {
        String id = equipped(p, "victory");
        ConfigurationSection c = id == null ? yml.getConfigurationSection("default-victory") : get("victory", id);
        if (c == null) {
            return;
        }
        List<Color> colors = new ArrayList<>();
        for (String hex : Yml.strings(c, "colors")) {
            colors.add(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)));
        }
        if (colors.isEmpty()) {
            colors.add(Color.YELLOW);
        }
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(c.getString("shape", "BALL_LARGE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = FireworkEffect.Type.BALL_LARGE;
        }
        int count = c.getInt("count", 3);
        FireworkEffect effect = FireworkEffect.builder().with(type).withColor(colors).trail(true).flicker(c.getBoolean("flicker", true)).build();
        for (int i = 0; i < count; i++) {
            int delay = i * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Location l = p.getLocation().add((Math.random() - 0.5) * 3, 1.5, (Math.random() - 0.5) * 3);
                Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
                    FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(effect);
                    meta.setPower(0);
                    f.setFireworkMeta(meta);
                    f.setPersistent(false);
                });
                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 3L);
            }, delay);
        }
        String particle = c.getString("particle");
        if (particle != null) {
            Actions.spawnParticle(Actions.particleType(particle), p.getLocation().add(0, 1, 0), 80, 1.2, 0.1,
                    c.getString("color", "#FFD700"), 1.4f);
        }
    }

    public void quit(Player p) {
        lastTrail.remove(p.getUniqueId());
    }

    /** Price (keys, gems) of a cosmetic; both 0 = not for sale. */
    public long[] price(String type, String id) {
        ConfigurationSection c = get(type, id);
        return c == null ? new long[]{0, 0} : new long[]{c.getLong("price-keys", 0), c.getLong("price-gems", 0)};
    }
}
