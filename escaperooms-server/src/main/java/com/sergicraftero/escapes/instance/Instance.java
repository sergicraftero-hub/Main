package com.sergicraftero.escapes.instance;

import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.Template;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** One physical copy of a room in the instance world. */
public final class Instance {

    public enum State { PASTING, READY, IN_USE, RESETTING }

    private final int row;
    private final int column;
    private final RoomDef room;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    private Template template;
    private State state = State.PASTING;
    private PasteJob job;

    public Instance(int row, int column, RoomDef room, Template template, World world, int ox, int oy, int oz) {
        this.row = row;
        this.column = column;
        this.room = room;
        this.template = template;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public String key() {
        return row + ":" + column;
    }

    public int row() {
        return row;
    }

    public int column() {
        return column;
    }

    public RoomDef room() {
        return room;
    }

    public Template template() {
        return template;
    }

    void template(Template t) {
        this.template = t;
    }

    public World world() {
        return world;
    }

    public State state() {
        return state;
    }

    void state(State s) {
        this.state = s;
    }

    PasteJob job() {
        return job;
    }

    void job(PasteJob job) {
        this.job = job;
    }

    public double pasteProgress() {
        return job == null ? 1.0 : job.progress();
    }

    public int originX() {
        return ox;
    }

    public int originY() {
        return oy;
    }

    public int originZ() {
        return oz;
    }

    public Block block(int[] rel) {
        return world.getBlockAt(ox + rel[0], oy + rel[1], oz + rel[2]);
    }

    public Location center(int[] rel) {
        return new Location(world, ox + rel[0] + 0.5, oy + rel[1] + 0.5, oz + rel[2] + 0.5);
    }

    /** Relative coordinates of a world block. */
    public int[] relative(Block b) {
        return new int[]{b.getX() - ox, b.getY() - oy, b.getZ() - oz};
    }

    public BoundingBox bounds() {
        return new BoundingBox(ox, oy, oz, ox + template.sizeX(), oy + template.sizeY(), oz + template.sizeZ());
    }

    public boolean contains(Location l) {
        return l.getWorld() == world && bounds().expand(2).contains(l.toVector());
    }

    public BoundingBox box(Template.Marker m) {
        return new BoundingBox(ox + m.x(), oy + m.y(), oz + m.z(), ox + m.x2() + 1, oy + m.y2() + 1, oz + m.z2() + 1);
    }
}
