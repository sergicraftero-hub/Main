package com.sergicraftero.escapes.it;

import net.kyori.adventure.text.Component;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

/** PlayerMock that accepts the resource pack request (MockBukkit doesn't implement it). */
public class TestPlayer extends PlayerMock {

    public int packRequests;

    public TestPlayer(ServerMock server, String name) {
        super(server, name);
    }

    @Override
    public void setResourcePack(UUID id, String url, byte[] hash, Component prompt, boolean force) {
        packRequests++;
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
    public <T> void spawnParticle(org.bukkit.Particle p, org.bukkit.Location l, int c, double ox, double oy, double oz, double e, T d, boolean f) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle p, double x, double y, double z, int c, double ox, double oy, double oz, double e, T d, boolean f) {
        // particles are cosmetic; MockBukkit doesn't implement them
    }

    private StrictPlayerInventory strictInventory;

    @Override
    public org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock getInventory() {
        if (strictInventory == null) {
            strictInventory = new StrictPlayerInventory(this);
        }
        return strictInventory;
    }
}
