package com.sergicraftero.escapes.util;

import java.text.Normalizer;
import java.util.Locale;

/** Small text helpers. */
public final class Text {

    private Text() {
    }

    /** Lower case, without accents, punctuation or repeated spaces: "¡Él es EL Mayordomo!" → "el es el mayordomo". */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    /** mm:ss or h:mm:ss. */
    public static String time(long millis) {
        long total = Math.max(0, millis / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /** "1h 05m", "12m", "45s". */
    public static String duration(long millis) {
        long total = Math.max(0, millis / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return h + "h " + String.format("%02dm", m);
        }
        if (m > 0) {
            return m + "m";
        }
        return s + "s";
    }

    public static String bar(double progress, int length, String full, String empty) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * length);
        return full.repeat(filled) + empty.repeat(length - filled);
    }

    public static String stars(int stars, int max) {
        return "<gold>" + "★".repeat(Math.max(0, stars)) + "<dark_gray>" + "★".repeat(Math.max(0, max - stars));
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    public static String number(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }
}
