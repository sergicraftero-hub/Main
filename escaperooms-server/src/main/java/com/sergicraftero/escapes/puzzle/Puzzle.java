package com.sergicraftero.escapes.puzzle;

import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime of one puzzle inside one game. Subclasses only react while {@link #isActive()}; the game routes
 * events to them. A puzzle "owns" the blocks it uses so they can't be used before it's active.
 */
public abstract class Puzzle {

    public enum State { LOCKED, ACTIVE, SOLVED }

    protected final PuzzleDef def;
    protected Game game;
    private State state = State.LOCKED;
    private final Set<Long> owned = new HashSet<>();

    protected Puzzle(PuzzleDef def) {
        this.def = def;
    }

    public PuzzleDef def() {
        return def;
    }

    public String id() {
        return def.id();
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean isSolved() {
        return state == State.SOLVED;
    }

    /** Called by the game. */
    public final void attach(Game game) {
        this.game = game;
        setup();
    }

    public final void setState(State s) {
        this.state = s;
    }

    // ───────────────────────── hooks ─────────────────────────

    /** Resolve positions, prepare blocks/entities. Runs once, right after the room is pasted. */
    protected abstract void setup();

    /** Requirements met: the puzzle can be played now. */
    public void onActivate() {
    }

    /** Solved (by players or forced by an action). */
    public void onSolved() {
    }

    /** Right/left click on a block. Return true when this puzzle handled it (the event is cancelled). */
    public boolean onInteract(Player p, Block b, boolean rightClick, ItemStack hand) {
        return false;
    }

    public boolean onEntity(Player p, Entity e, boolean rightClick, ItemStack hand) {
        return false;
    }

    /** Owns the entity (frames, NPCs) → nobody can touch it unless the puzzle handles it. */
    public boolean ownsEntity(Entity e) {
        return false;
    }

    /**
     * A chat message. {@code captured} is true when the player clicked this puzzle and the line was taken as
     * their answer; false for open team chat. Return true when it solved the puzzle.
     */
    public boolean onChat(Player p, String message, boolean captured) {
        return false;
    }

    public void onMove(Player p, Block to) {
    }

    /** A player's inventory changed (item given, picked from a chest...). */
    public void onInventory(Player p) {
    }

    /** Every 5 ticks while active. */
    public void tick() {
    }

    /** End of game: remove entities, cancel tasks. */
    public void cleanup() {
    }

    /** Fraction done for the objective list (e.g. 2 of 4 plates). -1 when not applicable. */
    public double progress() {
        return -1;
    }

    // ───────────────────────── helpers ─────────────────────────

    protected void own(Block b) {
        owned.add(key(b));
    }

    protected void own(List<Block> blocks) {
        blocks.forEach(this::own);
    }

    public boolean owns(Block b) {
        return owned.contains(key(b));
    }

    private static long key(Block b) {
        return ((long) b.getX() & 0x3FFFFFF) | (((long) b.getZ() & 0x3FFFFFF) << 26) | (((long) b.getY() & 0xFFF) << 52);
    }

    protected void solve(Player p) {
        game.solve(def.id(), p);
    }

    protected void fail(Player p) {
        game.puzzleFailed(this, p);
    }

    protected String str(String key, String def) {
        return this.def.str(key, def);
    }

    protected int integer(String key, int def) {
        return this.def.section().getInt(key, def);
    }

    protected boolean bool(String key, boolean def) {
        return this.def.section().getBoolean(key, def);
    }

    protected List<String> list(String key) {
        return this.def.list(key);
    }

    /** Message from the puzzle YAML key, or the default messages.yml key. */
    protected void tell(Player p, String yamlKey, String defaultMessageKey, Object... pairs) {
        String custom = def.section().getString(yamlKey);
        if (custom != null) {
            if (!custom.isEmpty()) {
                p.sendMessage(Msg.parse(custom, pairs));
            }
        } else {
            Msg.send(p, defaultMessageKey, pairs);
        }
    }
}
