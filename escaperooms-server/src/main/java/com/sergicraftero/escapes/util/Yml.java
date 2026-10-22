package com.sergicraftero.escapes.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** YAML helpers: lists of maps become {@link ConfigurationSection}s so every config object reads the same way. */
public final class Yml {

    private Yml() {
    }

    @SuppressWarnings("unchecked")
    public static ConfigurationSection section(Map<?, ?> map) {
        MemoryConfiguration mem = new MemoryConfiguration();
        return mem.createSection("s", (Map<String, Object>) map);
    }

    /** A list of sections under {@code key}: accepts a YAML list of maps or a map of maps (keys become "id"). */
    public static List<ConfigurationSection> sections(ConfigurationSection parent, String key) {
        List<ConfigurationSection> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        if (parent.isList(key)) {
            for (Object o : parent.getList(key, List.of())) {
                if (o instanceof Map<?, ?> m) {
                    out.add(section(m));
                } else if (o instanceof String s) {
                    // shorthand: "- win" or "- message: hola"
                    MemoryConfiguration mem = new MemoryConfiguration();
                    ConfigurationSection sec = mem.createSection("s");
                    sec.set("type", s);
                    out.add(sec);
                }
            }
        } else if (parent.isConfigurationSection(key)) {
            ConfigurationSection map = parent.getConfigurationSection(key);
            for (String k : map.getKeys(false)) {
                ConfigurationSection child = map.getConfigurationSection(k);
                if (child != null) {
                    if (!child.contains("id")) {
                        child.set("id", k);
                    }
                    out.add(child);
                }
            }
        }
        return out;
    }

    /** Strings from either a single value or a list. */
    public static List<String> strings(ConfigurationSection s, String key) {
        if (s == null || !s.contains(key)) {
            return List.of();
        }
        if (s.isList(key)) {
            List<String> out = new ArrayList<>();
            for (Object o : s.getList(key, List.of())) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return List.of(String.valueOf(s.get(key)));
    }

    /** Parses durations like "90", "90s", "5m", "1h", "2t" (ticks). Result in ticks. */
    public static long ticks(Object value, long def) {
        if (value == null) {
            return def;
        }
        String v = String.valueOf(value).trim().toLowerCase();
        try {
            if (v.endsWith("t")) {
                return Long.parseLong(v.substring(0, v.length() - 1));
            }
            if (v.endsWith("ms")) {
                return Long.parseLong(v.substring(0, v.length() - 2)) / 50;
            }
            if (v.endsWith("s")) {
                return (long) (Double.parseDouble(v.substring(0, v.length() - 1)) * 20);
            }
            if (v.endsWith("m")) {
                return (long) (Double.parseDouble(v.substring(0, v.length() - 1)) * 1200);
            }
            if (v.endsWith("h")) {
                return (long) (Double.parseDouble(v.substring(0, v.length() - 1)) * 72000);
            }
            return (long) (Double.parseDouble(v) * 20); // plain numbers are seconds
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static long seconds(Object value, long def) {
        long t = ticks(value, def * 20);
        return t / 20;
    }
}
