package com.sergicraftero.escapes.profile;

import com.sergicraftero.escapes.EscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Cache of online players' profiles; loads at pre-login, saves on quit and periodically. */
public final class ProfileService {

    private final EscapePlugin plugin;
    private final Storage storage;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileService(EscapePlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        long period = 20L * 120;
        Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, period, period);
    }

    /** Called from AsyncPlayerPreLoginEvent. */
    public void preload(UUID uuid, String name) {
        PlayerProfile p = storage.load(uuid, name);
        cache.put(uuid, p);
    }

    public PlayerProfile get(Player p) {
        PlayerProfile profile = cache.get(p.getUniqueId());
        if (profile == null) {
            // Should not happen (pre-login loads it) but never hand out null.
            profile = storage.load(p.getUniqueId(), p.getName());
            cache.put(p.getUniqueId(), profile);
        }
        return profile;
    }

    public PlayerProfile cached(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerProfile> online() {
        return cache.values();
    }

    public void joined(Player p) {
        PlayerProfile profile = get(p);
        profile.name = p.getName();
        profile.lastJoin = System.currentTimeMillis();
        profile.sessionStart = System.currentTimeMillis();
        profile.dirty = true;
    }

    public void quit(Player p) {
        PlayerProfile profile = cache.remove(p.getUniqueId());
        if (profile != null) {
            if (profile.sessionStart > 0) {
                profile.playtimeMs += System.currentTimeMillis() - profile.sessionStart;
            }
            storage.saveAsync(profile);
        }
    }

    private void autosave() {
        long now = System.currentTimeMillis();
        for (PlayerProfile p : cache.values()) {
            if (p.sessionStart > 0) {
                p.playtimeMs += now - p.sessionStart;
                p.sessionStart = now;
                p.dirty = true;
            }
            if (p.dirty) {
                storage.saveAsync(p);
            }
        }
    }

    public void saveAllSync() {
        long now = System.currentTimeMillis();
        for (PlayerProfile p : cache.values()) {
            if (p.sessionStart > 0) {
                p.playtimeMs += now - p.sessionStart;
                p.sessionStart = now;
            }
            storage.saveSync(p);
        }
    }

    /**
     * Edits a profile that may be offline (store purchases, admin grants). Online players are edited in the
     * cache; offline ones are loaded, edited and saved in the background. {@code done} runs on the main thread.
     */
    public void edit(OfflinePlayer target, Consumer<PlayerProfile> change, Runnable done) {
        PlayerProfile online = cache.get(target.getUniqueId());
        if (online != null) {
            change.accept(online);
            online.dirty = true;
            if (done != null) {
                done.run();
            }
            return;
        }
        storage.runAsync(() -> {
            PlayerProfile p = storage.load(target.getUniqueId(), target.getName() == null ? "?" : target.getName());
            change.accept(p);
            storage.saveSync(p);
            if (done != null) {
                Bukkit.getScheduler().runTask(plugin, done);
            }
        });
    }
}
