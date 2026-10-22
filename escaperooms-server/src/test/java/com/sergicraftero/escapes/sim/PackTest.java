package com.sergicraftero.escapes.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sergicraftero.escapes.pack.ModelData;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated pack and the plugin agree on custom model data numbers, and every model has its files. */
class PackTest {

    private static Path pack() {
        Path p = Paths.get("resourcepack");
        return Files.isDirectory(p) ? p : Paths.get("/home/user/Main/escaperooms-server/resourcepack");
    }

    @Test
    void customModelDataMatchesPlugin() throws Exception {
        Path items = pack().resolve("assets/minecraft/items");
        List<Path> files;
        try (Stream<Path> s = Files.list(items)) {
            files = s.toList();
        }
        assertTrue(files.size() > 10, "no item definitions generated");
        int checked = 0;
        for (Path f : files) {
            JsonObject model = JsonParser.parseString(Files.readString(f)).getAsJsonObject().getAsJsonObject("model");
            JsonArray entries = model.getAsJsonArray("entries");
            for (JsonElement e : entries) {
                JsonObject entry = e.getAsJsonObject();
                int threshold = entry.get("threshold").getAsInt();
                String target = entry.getAsJsonObject("model").get("model").getAsString(); // escape:item/key_brass
                String name = target.substring(target.indexOf("item/") + 5);
                assertEquals(ModelData.of("escape:" + name), threshold, f.getFileName() + " " + name);
                assertTrue(Files.exists(pack().resolve("assets/escape/models/item/" + name + ".json")), "model file " + name);
                checked++;
            }
        }
        System.out.println("pack: " + checked + " custom models verified");
    }
}
