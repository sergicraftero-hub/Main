package com.sergicraftero.escapes.it;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.block.state.BlockStateMock;

/** BlockMock that, like a real server, creates the block entity (chest, lectern, sign) when the type changes. */
public class TestBlock extends BlockMock {

    public TestBlock(Material material, Location location) {
        super(material, location);
    }

    /** A real server hands out a fresh Location each call; MockBukkit returns its own (mutable) field. */
    @Override
    public Location getLocation() {
        return super.getLocation().clone();
    }

    @Override
    public void setBlockData(BlockData data, boolean applyPhysics) {
        Material before = getType();
        super.setBlockData(data, applyPhysics);
        if (before != data.getMaterial()) {
            setState(BlockStateMock.mockState(this));
        }
    }

    @Override
    public void setType(Material type, boolean applyPhysics) {
        Material before = getType();
        super.setType(type, applyPhysics);
        if (before != type) {
            setState(BlockStateMock.mockState(this));
        }
    }
}
