package com.sergicraftero.escaperoom.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers for working with YAML lists of maps. */
public final class Cfg {

    private Cfg() {
    }

    public static ConfigurationSection section(Map<?, ?> map) {
        MemoryConfiguration root = new MemoryConfiguration();
        return root.createSection("s", map);
    }

    public static List<ConfigurationSection> sections(ConfigurationSection parent, String path) {
        List<ConfigurationSection> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Map<?, ?> map : parent.getMapList(path)) {
            out.add(section(map));
        }
        return out;
    }

    public static List<String> stringList(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return List.of();
        }
        if (section.isList(path)) {
            return section.getStringList(path);
        }
        String single = section.getString(path);
        return single == null ? List.of() : List.of(single);
    }
}
