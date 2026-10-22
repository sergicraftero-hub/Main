package com.sergicraftero.escapes.util;

/**
 * Runs purely visual tweaks (display billboard, scale, brightness...). A failure there must never break a
 * game: the entity still exists and works, it just looks plainer.
 */
public final class Cosmetic {

    private Cosmetic() {
    }

    public static void apply(Runnable visual) {
        try {
            visual.run();
        } catch (RuntimeException ignored) {
            // unsupported on this server build (or a test server): keep the default look
        }
    }
}
