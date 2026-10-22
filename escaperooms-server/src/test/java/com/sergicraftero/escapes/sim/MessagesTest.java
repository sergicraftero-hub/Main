package com.sergicraftero.escapes.sim;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every message key used in the code exists in messages.yml (players never see "[game.xyz]"). */
class MessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"((?:lobby|chat|pack|menu|cosmetics|notebook|recipes|room|queue|game|hints|puzzle|props|rewards|level|daily|quests|achievements|shop|party|parkour|admin|currency)\\.[a-z0-9.-]+)\"");

    @Test
    @SuppressWarnings("unchecked")
    void everyKeyExists() throws Exception {
        Set<String> keys = new HashSet<>();
        try (InputStream in = MessagesTest.class.getResourceAsStream("/messages.yml")) {
            flatten("", (Map<String, Object>) new Yaml().load(in), keys);
        }
        Set<String> config = new HashSet<>();
        for (String f : List.of("/config.yml", "/menus.yml", "/progression.yml")) {
            try (InputStream in = MessagesTest.class.getResourceAsStream(f)) {
                flatten("", (Map<String, Object>) new Yaml().load(in), config);
            }
        }
        Path src = Paths.get(System.getProperty("src.dir", "src/main/java"));
        if (!Files.isDirectory(src)) {
            src = Paths.get("/home/user/Main/escaperooms-server/src/main/java");
        }
        Set<String> missing = new TreeSet<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        for (Path f : files) {
            String name = f.getFileName().toString();
            if (name.equals("RoomDef.java") || name.endsWith("Builder.java")) {
                continue; // room YAML paths and map markers, not messages
            }
            Matcher m = LITERAL.matcher(Files.readString(f));
            while (m.find()) {
                String key = m.group(1);
                if (keys.contains(key) || config.contains(key) || isConfigPrefix(config, key) || key.endsWith(".yml")) {
                    continue;
                }
                missing.add(key + "  (" + f.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "Missing in messages.yml:\n" + String.join("\n", missing));
        System.out.println("messages.yml: " + keys.size() + " keys, all used keys present");
    }

    private static boolean isConfigPrefix(Set<String> config, String key) {
        for (String c : config) {
            if (c.startsWith(key + ".")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<?, ?> map, Set<String> out) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> m) {
                out.add(k);
                flatten(k, (Map<String, Object>) m, out);
            } else {
                out.add(k);
            }
        }
    }
}
