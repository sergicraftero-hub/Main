package com.sergicraftero.escapes.template;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Reads and writes {@link Template}s as gzip-compressed {@code .ert} files. */
public final class TemplateIO {

    private static final int MAGIC = 0x45525431; // "ERT1"
    private static final int VERSION = 1;
    private static final Gson GSON = new Gson();
    private static final Type TILE_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private TemplateIO() {
    }

    public static void write(Template t, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(tmp)) {
            write(t, raw);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static void write(Template t, OutputStream raw) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)));
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(t.sizeX());
        out.writeInt(t.sizeY());
        out.writeInt(t.sizeZ());
        out.writeInt(t.palette().size());
        for (String p : t.palette()) {
            out.writeUTF(p);
        }
        // Run-length encoding: rooms are mostly air or long walls.
        int[] blocks = t.rawBlocks();
        int i = 0;
        while (i < blocks.length) {
            int id = blocks[i];
            int run = 1;
            while (i + run < blocks.length && blocks[i + run] == id && run < Integer.MAX_VALUE) {
                run++;
            }
            writeVarInt(out, id);
            writeVarInt(out, run);
            i += run;
        }
        out.writeInt(t.markers().size());
        for (Map.Entry<String, Template.Marker> e : t.markers().entrySet()) {
            Template.Marker m = e.getValue();
            out.writeUTF(e.getKey());
            out.writeInt(m.x());
            out.writeInt(m.y());
            out.writeInt(m.z());
            out.writeInt(m.x2());
            out.writeInt(m.y2());
            out.writeInt(m.z2());
            out.writeUTF(m.facing() == null ? "" : m.facing());
        }
        out.writeInt(t.tiles().size());
        for (Map.Entry<Long, Map<String, Object>> e : t.tiles().entrySet()) {
            out.writeLong(e.getKey());
            byte[] json = GSON.toJson(e.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(json.length);
            out.write(json);
        }
        out.flush();
        out.close();
    }

    public static Template read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    public static Template read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
        if (in.readInt() != MAGIC) {
            throw new IOException("Not an escape room template");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported template version " + version);
        }
        Template t = new Template(in.readInt(), in.readInt(), in.readInt());
        int paletteSize = in.readInt();
        int[] remap = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            remap[i] = t.paletteId(in.readUTF());
        }
        int[] blocks = t.rawBlocks();
        int i = 0;
        while (i < blocks.length) {
            int id = remap[readVarInt(in)];
            int run = readVarInt(in);
            if (run <= 0 || i + run > blocks.length) {
                throw new IOException("Corrupt template block data");
            }
            java.util.Arrays.fill(blocks, i, i + run, id);
            i += run;
        }
        int markers = in.readInt();
        for (int m = 0; m < markers; m++) {
            String name = in.readUTF();
            int x = in.readInt();
            int y = in.readInt();
            int z = in.readInt();
            int x2 = in.readInt();
            int y2 = in.readInt();
            int z2 = in.readInt();
            String facing = in.readUTF();
            t.markers().put(name, new Template.Marker(x, y, z, x2, y2, z2, facing.isEmpty() ? null : facing));
        }
        int tiles = in.readInt();
        for (int k = 0; k < tiles; k++) {
            long key = in.readLong();
            byte[] json = new byte[in.readInt()];
            in.readFully(json);
            Map<String, Object> data = GSON.fromJson(new String(json, java.nio.charset.StandardCharsets.UTF_8), TILE_TYPE);
            t.tiles().put(key, data);
        }
        return t;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        }
    }
}
