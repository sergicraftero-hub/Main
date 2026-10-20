package com.sergicraftero.escaperoom.display;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.action.Actions;
import com.sergicraftero.escaperoom.room.AmbientEffect;
import com.sergicraftero.escaperoom.room.Room;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Plays each room's looping ambient particles and sounds whenever a player is nearby. */
public final class AmbientService {

    private static final int PERIOD = 5;

    private final EscapeRoomPlugin plugin;
    private final BukkitTask task;
    private long ticks;

    public AmbientService(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 40L, PERIOD);
    }

    private void run() {
        ticks += PERIOD;
        double radius = plugin.getConfig().getDouble("ambient.radius", 28);
        double radiusSq = radius * radius;
        for (Room room : plugin.rooms().all()) {
            if (room.ambient().isEmpty()) {
                continue;
            }
            World world = room.world();
            if (world == null) {
                continue;
            }
            for (AmbientEffect effect : room.ambient()) {
                if (ticks % effect.interval() >= PERIOD) {
                    continue;
                }
                if (effect.chance() < 1.0 && ThreadLocalRandom.current().nextDouble() > effect.chance()) {
                    continue;
                }
                Location at = new Location(world, effect.x(), effect.y(), effect.z());
                List<Player> near = new ArrayList<>();
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(at) <= radiusSq) {
                        near.add(p);
                    }
                }
                if (near.isEmpty()) {
                    continue;
                }
                if (effect.particle() != null) {
                    Actions.spawnParticle(world, effect.particle(), at, effect.count(), effect.offset(), effect.speed(),
                            effect.data());
                }
                if (effect.sound() != null) {
                    near.forEach(p -> p.playSound(effect.sound(), at.getX(), at.getY(), at.getZ()));
                }
            }
        }
    }

    public void shutdown() {
        task.cancel();
    }
}
