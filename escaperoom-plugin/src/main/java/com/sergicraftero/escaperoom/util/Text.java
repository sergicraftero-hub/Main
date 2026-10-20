package com.sergicraftero.escaperoom.util;

import java.text.Normalizer;
import java.util.Locale;

/** Text helpers: accent/case-insensitive normalization and time formatting. */
public final class Text {

    private Text() {
    }

    /** Lower case, strips accents and punctuation, collapses whitespace. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        return n.trim().replaceAll("\\s+", " ");
    }

    public static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    public static String formatTimePrecise(long millis) {
        long ms = Math.max(0, millis);
        return String.format(Locale.ROOT, "%02d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100);
    }
}
