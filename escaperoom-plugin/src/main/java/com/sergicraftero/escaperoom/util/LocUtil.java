package com.sergicraftero.escaperoom.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** Serialization helpers for locations stored without world ("x,y,z[,yaw,pitch]"). */
public final class LocUtil {

    private LocUtil() {
    }

    public static Location parse(World world, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] p = raw.replace(" ", "").split(",");
        double x = Double.parseDouble(p[0]);
        double y = Double.parseDouble(p[1]);
        double z = Double.parseDouble(p[2]);
        float yaw = p.length > 3 ? Float.parseFloat(p[3]) : 0f;
        float pitch = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static String serialize(Location loc) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
