package com.sergicraftero.escapes.it;

import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashSet;
import java.util.Set;

/** WorldMock that keeps track of plugin chunk tickets (MockBukkit doesn't implement them). */
public class TestWorld extends WorldMock {

    private final Set<Long> tickets = new HashSet<>();

    public TestWorld(WorldCreator creator) {
        super(creator);
    }

    private final java.util.Map<org.mockbukkit.mockbukkit.world.Coordinate, TestBlock> blocks = new java.util.HashMap<>();

    /** Our own block store so every block is a {@link TestBlock} that keeps its state. */
    @Override
    public org.mockbukkit.mockbukkit.block.BlockMock getBlockAt(org.mockbukkit.mockbukkit.world.Coordinate c) {
        return blocks.computeIfAbsent(c, k -> new TestBlock(super.createBlock(k).getType(),
                new org.bukkit.Location(this, k.x, k.y, k.z)));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    @Override
    public boolean addPluginChunkTicket(int x, int z, Plugin plugin) {
        getChunkAt(x, z);
        return tickets.add(key(x, z));
    }

    @Override
    public boolean removePluginChunkTicket(int x, int z, Plugin plugin) {
        return tickets.remove(key(x, z));
    }

    public int ticketCount() {
        return tickets.size();
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz, double e) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz, double e) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz, double e, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz, double e, T d) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, java.util.List<org.bukkit.entity.Player> r, org.bukkit.entity.Player s, double x, double y, double z, int c, double ox, double oy, double oz, double e, T d, boolean f) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz, double e, T d, boolean f) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz, double e, T d, boolean f) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }
}
