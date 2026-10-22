package com.sergicraftero.escapes.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/** Detects Bedrock players (Geyser + Floodgate) without a compile-time dependency. */
public final class Bedrock {

    private static Object api;
    private static Method isFloodgate;

    private Bedrock() {
    }

    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = c.getMethod("getInstance").invoke(null);
            isFloodgate = c.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            api = null;
        }
    }

    public static boolean is(Player p) {
        if (api != null) {
            try {
                return (Boolean) isFloodgate.invoke(api, p.getUniqueId());
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        // Floodgate UUIDs have the most significant bits set to 0.
        return p.getUniqueId().getMostSignificantBits() == 0;
    }
}
