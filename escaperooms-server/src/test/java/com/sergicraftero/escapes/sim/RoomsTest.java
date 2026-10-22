package com.sergicraftero.escapes.sim;

import com.sergicraftero.escapes.build.Builders;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import com.sergicraftero.escapes.template.TemplateIO;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Every bundled room: its YAML matches its generated map, and a simulated player can finish it. */
class RoomsTest {

    static final Set<String> TYPES = Set.of("interact", "keypad", "levers", "sequence", "simon", "plates", "frames",
            "deliver", "answer", "reach", "obtain", "gate", "lightsout", "melody", "choice", "memory", "slide");

    static List<String> roomIds() throws Exception {
        try (InputStream in = RoomsTest.class.getResourceAsStream("/rooms/index.txt")) {
            assertNotNull(in, "rooms/index.txt missing");
            List<String> out = new ArrayList<>();
            for (String s : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\s+")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> roomYaml(String id) throws Exception {
        try (InputStream in = RoomsTest.class.getResourceAsStream("/rooms/" + id + ".yml")) {
            assertNotNull(in, "rooms/" + id + ".yml missing");
            return (Map<String, Object>) new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @TestFactory
    Stream<DynamicTest> roomsAreConsistentAndSolvable() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String id : roomIds()) {
            tests.add(DynamicTest.dynamicTest(id, () -> checkRoom(id)));
        }
        return tests.stream();
    }

    private void checkRoom(String id) throws Exception {
        Map<String, Object> room = roomYaml(id);
        String templateId = String.valueOf(room.getOrDefault("template", id));
        assertTrue(Builders.has(templateId), "no builder for " + templateId);
        Template t = Builders.build(templateId);
        List<String> problems = new ArrayList<>(StaticChecks.check(room, t));
        RoomSimulator sim = new RoomSimulator(t, room);
        sim.run();
        problems.addAll(sim.problems);
        if (!problems.isEmpty()) {
            System.out.println("[" + id + "] simulation log: " + sim.log);
            fail("[" + id + "] " + problems.size() + " problem(s):\n - " + String.join("\n - ", problems));
        }
        int puzzles = RoomSimulator.maps(room, "puzzles").size();
        System.out.println("[" + id + "] OK: " + puzzles + " puzzles, " + t.markers().size() + " markers, map "
                + t.sizeX() + "x" + t.sizeY() + "x" + t.sizeZ() + ", order: " + sim.log);
    }

    /** The simulator must notice a room that can't be finished (here: the first door never opens). */
    @Test
    @SuppressWarnings("unchecked")
    void simulatorCatchesBlockedRooms() throws Exception {
        Map<String, Object> room = roomYaml("tutorial");
        Map<String, Object> first = (Map<String, Object>) ((Map<String, Object>) room.get("puzzles")).get("t_llave");
        first.put("on-solve", List.of());
        RoomSimulator sim = new RoomSimulator(Builders.build("tutorial"), room);
        assertTrue(!sim.run(), "a room with a door that never opens passed the simulation");
        assertTrue(sim.problems.stream().anyMatch(p -> p.contains("t_codigo")), sim.problems.toString());
    }

    @Test
    void everyBlockExistsInMinecraft() throws Exception {
        List<String> unknown = new ArrayList<>();
        for (String id : Builders.ids()) {
            for (String data : Builders.build(id).palette()) {
                int bracket = data.indexOf('[');
                String name = (bracket < 0 ? data : data.substring(0, bracket)).replace("minecraft:", "");
                org.bukkit.Material m = org.bukkit.Material.getMaterial(name.toUpperCase(java.util.Locale.ROOT));
                if (m == null || !m.isBlock()) {
                    unknown.add(id + ": " + data);
                }
            }
        }
        assertTrue(unknown.isEmpty(), "blocks that don't exist in this Minecraft version: " + unknown);
    }

    @Test
    void templatesRoundTrip() throws Exception {
        for (String id : Builders.ids()) {
            Template t = Builders.build(id);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            TemplateIO.write(t, out);
            Template back = TemplateIO.read(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(t.sizeX(), back.sizeX());
            assertEquals(t.markers(), back.markers());
            for (int y = 0; y < t.sizeY(); y += 3) {
                for (int z = 0; z < t.sizeZ(); z += 2) {
                    for (int x = 0; x < t.sizeX(); x++) {
                        assertEquals(t.get(x, y, z), back.get(x, y, z), id + " block " + x + "," + y + "," + z);
                    }
                }
            }
            assertEquals(t.tiles().size(), back.tiles().size());
            System.out.println("template " + id + ": " + out.size() / 1024 + " KB");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void lobbyMarkersExist() throws Exception {
        Template t = Builders.build("lobby");
        Map<String, Object> lobby;
        try (InputStream in = RoomsTest.class.getResourceAsStream("/lobby.yml")) {
            lobby = (Map<String, Object>) new Yaml().load(in);
        }
        List<String> missing = new ArrayList<>();
        collectRefs(lobby, missing, t);
        assertTrue(missing.isEmpty(), "lobby.yml refers to missing markers: " + missing);
        // every portal and NPC is reachable on foot from the spawn
        RoomSimulator sim = new RoomSimulator(t, Map.of("spawn", "@spawn"));
        sim.starts.add(Refs.point("@spawn", t));
        sim.reachable = sim.explore();
        for (String portal : List.of("@portal.cine", "@portal.anime", "@portal.videojuegos", "@portal.originales", "@parkour.start")) {
            assertTrue(sim.regionReachable(portal), portal + " not reachable from spawn");
        }
        for (String npc : List.of("@npc.guide", "@npc.shop", "@npc.quests", "@npc.daily", "@npc.cosmetics", "@npc.top")) {
            int[] p = Refs.point(npc, t);
            assertTrue(sim.usable(p[0], p[1] + 1, p[2]), npc + " can't be clicked");
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectRefs(Object o, List<String> missing, Template t) {
        if (o instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                collectRefs(v, missing, t);
            }
        } else if (o instanceof List<?> l) {
            for (Object v : l) {
                collectRefs(v, missing, t);
            }
        } else if (o instanceof String s && s.startsWith("@") && !Refs.exists(s, t)) {
            missing.add(s);
        }
    }
}
