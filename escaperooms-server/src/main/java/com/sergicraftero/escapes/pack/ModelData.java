package com.sergicraftero.escapes.pack;

import java.util.Locale;

/**
 * Custom model data number of a resource pack model ("escape:key_brass"). tools/generate_pack.py computes the
 * same value for the range_dispatch entries it writes, so both sides agree without a lookup table.
 */
public final class ModelData {

    private ModelData() {
    }

    public static int of(String model) {
        String key = model.toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "escape:" + key;
        }
        return Math.abs(key.hashCode() % 9_000_000) + 1_000_000;
    }
}
