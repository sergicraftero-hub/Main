package com.sergicraftero.escapes.template;

import com.sergicraftero.escapes.build.Builders;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Loads templates from {@code templates/<id>.ert}. When a file is missing and a built-in builder exists with that
 * id, the template is generated and saved, so a fresh install has every map ready.
 */
public final class TemplateStore {

    public record Loaded(Template template, BlockData[] palette, long version) {
    }

    private final Path dir;
    private final Logger log;
    private final Map<String, Loaded> cache = new ConcurrentHashMap<>();

    public TemplateStore(Path dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    public Path file(String id) {
        return dir.resolve(id + ".ert");
    }

    public boolean exists(String id) {
        return cache.containsKey(id) || Files.exists(file(id)) || Builders.has(id);
    }

    /** Must be called on the main thread (block data creation). */
    public Loaded get(String id) {
        Loaded loaded = cache.get(id);
        if (loaded != null) {
            return loaded;
        }
        Template t = null;
        Path f = file(id);
        if (Files.exists(f)) {
            try {
                t = TemplateIO.read(f);
            } catch (IOException e) {
                log.severe("Template " + id + " could not be read: " + e.getMessage());
            }
        }
        if (t == null && Builders.has(id)) {
            t = generate(id);
        }
        if (t == null) {
            return null;
        }
        return put(id, t);
    }

    /** Template data without converting the palette (safe off the main thread, used by validation). */
    public Template peek(String id) {
        Loaded loaded = cache.get(id);
        if (loaded != null) {
            return loaded.template();
        }
        try {
            if (Files.exists(file(id))) {
                return TemplateIO.read(file(id));
            }
        } catch (IOException e) {
            return null;
        }
        return Builders.has(id) ? Builders.build(id) : null;
    }

    public Template generate(String id) {
        long start = System.currentTimeMillis();
        Template t = Builders.build(id);
        try {
            TemplateIO.write(t, file(id));
        } catch (IOException e) {
            log.severe("Template " + id + " could not be saved: " + e.getMessage());
        }
        log.info("Generated map '" + id + "' (" + t.sizeX() + "x" + t.sizeY() + "x" + t.sizeZ() + ", "
                + t.markers().size() + " markers) in " + (System.currentTimeMillis() - start) + " ms");
        return t;
    }

    /** Replaces a template (editor save, rebuild) and persists it. */
    public Loaded replace(String id, Template t) throws IOException {
        TemplateIO.write(t, file(id));
        return put(id, t);
    }

    private Loaded put(String id, Template t) {
        Loaded old = cache.get(id);
        Loaded l = new Loaded(t, BlockPalette.convert(t.palette(), log, id), old == null ? 1 : old.version() + 1);
        cache.put(id, l);
        return l;
    }

    public void clearCache() {
        cache.clear();
    }
}
