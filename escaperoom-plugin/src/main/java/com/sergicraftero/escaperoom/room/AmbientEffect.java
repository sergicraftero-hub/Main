package com.sergicraftero.escaperoom.room;

import com.sergicraftero.escaperoom.action.Actions;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** A looping particle and/or sound at a point of the room ("dripping water", "fire crackle"...). */
public record AmbientEffect(double x, double y, double z, Particle particle, int count, double offset, double speed,
                            Object data, Sound sound, int interval, double chance) {

    public static AmbientEffect load(ConfigurationSection s) {
        String[] p = s.getString("location", "0,0,0").replace(" ", "").split(",");
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            v[i] = Double.parseDouble(p[i]) + (p[i].contains(".") ? 0 : 0.5);
        }
        Particle particle = null;
        Object data = null;
        if (s.contains("particle")) {
            particle = Particle.valueOf(s.getString("particle").toUpperCase(Locale.ROOT));
            data = Actions.particleData(particle, s.getString("color", "#FFFFFF"), (float) s.getDouble("size", 1.0));
        }
        Sound sound = null;
        if (s.contains("sound")) {
            sound = Sound.sound(Key.key(s.getString("sound")), Sound.Source.AMBIENT,
                    (float) s.getDouble("volume", 0.5), (float) s.getDouble("pitch", 1.0));
        }
        return new AmbientEffect(v[0], v[1], v[2], particle, s.getInt("count", 3), s.getDouble("offset", 0.3),
                s.getDouble("speed", 0.0), data, sound, Math.max(5, s.getInt("interval", 40)),
                s.getDouble("chance", 1.0));
    }
}
