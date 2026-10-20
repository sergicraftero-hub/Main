package com.sergicraftero.escaperoom.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void normalizeIgnoresCaseAccentsAndPunctuation() {
        assertEquals("mercurio", Text.normalize("  ¡MERCÚRIO!  "));
        assertEquals("el azogue", Text.normalize("El   azogue..."));
    }

    @Test
    void formatsTimes() {
        assertEquals("30:00", Text.formatTime(1_800_000));
        assertEquals("01:05.4", Text.formatTimePrecise(65_432));
    }

    @Test
    void cuboidContainsItsCorners() {
        Cuboid c = Cuboid.of(new BlockPos(5, 70, -3), new BlockPos(1, 60, 4));
        assertTrue(c.contains(1, 60, -3));
        assertTrue(c.contains(5, 70, 4));
        assertFalse(c.contains(6, 65, 0));
        assertEquals(5 * 11 * 8, c.volume());
        assertEquals(new BlockPos(-2, 64, 7), BlockPos.parse(" -2, 64 ,7"));
    }
}
