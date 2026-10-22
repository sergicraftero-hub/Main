package com.sergicraftero.escapes.template;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolves position references used in room YAML against a template:
 * <ul>
 *   <li>{@code "@name"} — a marker (a point, or every block of a box marker when a list is asked)</li>
 *   <li>{@code "@name+1,0,-2"} — a marker with an offset</li>
 *   <li>{@code "@lamp.*"} — every marker starting with {@code lamp.}, in natural order (lamp.2 before lamp.10)</li>
 *   <li>{@code "12,3,40"} — raw template coordinates</li>
 *   <li>{@code "1,2,3:5,6,7"} — a raw box</li>
 * </ul>
 * Pure Java so the YAML can be validated in tests.
 */
public final class Refs {

    private Refs() {
    }

    /** First (or only) point of a reference. */
    public static int[] point(String ref, Template t) {
        List<int[]> pts = points(ref, t);
        if (pts.isEmpty()) {
            throw new IllegalArgumentException("Unknown position '" + ref + "'");
        }
        Template.Marker m = ref.startsWith("@") && !ref.contains("*") ? marker(ref, t) : null;
        if (m != null && m.isBox()) {
            int[] off = offset(ref);
            return new int[]{(m.x() + m.x2()) / 2 + off[0], m.y() + off[1], (m.z() + m.z2()) / 2 + off[2]};
        }
        return pts.get(0);
    }

    /** Facing stored on a point marker, or {@code null}. */
    public static String facing(String ref, Template t) {
        if (ref == null || !ref.startsWith("@") || ref.contains("*")) {
            return null;
        }
        Template.Marker m = marker(ref, t);
        return m == null ? null : m.facing();
    }

    public static List<int[]> points(String ref, Template t) {
        List<int[]> out = new ArrayList<>();
        if (ref == null || ref.isBlank()) {
            return out;
        }
        ref = ref.trim();
        if (ref.startsWith("@")) {
            String body = stripOffset(ref.substring(1));
            int[] off = offset(ref);
            if (body.endsWith("*")) {
                String prefix = body.substring(0, body.length() - 1);
                List<Map.Entry<String, Template.Marker>> matches = new ArrayList<>();
                for (Map.Entry<String, Template.Marker> e : t.markers().entrySet()) {
                    if (e.getKey().startsWith(prefix)) {
                        matches.add(e);
                    }
                }
                matches.sort(Comparator.comparing(Map.Entry::getKey, Refs::natural));
                for (Map.Entry<String, Template.Marker> e : matches) {
                    Template.Marker m = e.getValue();
                    out.add(new int[]{m.x() + off[0], m.y() + off[1], m.z() + off[2]});
                }
                return out;
            }
            Template.Marker m = t.marker(body);
            if (m == null) {
                return out;
            }
            for (int y = m.y(); y <= m.y2(); y++) {
                for (int z = m.z(); z <= m.z2(); z++) {
                    for (int x = m.x(); x <= m.x2(); x++) {
                        out.add(new int[]{x + off[0], y + off[1], z + off[2]});
                    }
                }
            }
            return out;
        }
        if (ref.contains(":")) {
            Template.Marker box = box(ref, t);
            if (box != null) {
                for (int y = box.y(); y <= box.y2(); y++) {
                    for (int z = box.z(); z <= box.z2(); z++) {
                        for (int x = box.x(); x <= box.x2(); x++) {
                            out.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            return out;
        }
        out.add(parseTriple(ref));
        return out;
    }

    public static List<int[]> points(List<String> refs, Template t) {
        List<int[]> out = new ArrayList<>();
        for (String r : refs) {
            out.addAll(points(r, t));
        }
        return out;
    }

    /** A box: a box marker, a point marker (1×1×1) or {@code "x,y,z:x,y,z"}. */
    public static Template.Marker box(String ref, Template t) {
        if (ref == null) {
            return null;
        }
        ref = ref.trim();
        if (ref.startsWith("@")) {
            Template.Marker m = marker(ref, t);
            if (m == null) {
                return null;
            }
            int[] off = offset(ref);
            return Template.Marker.box(m.x() + off[0], m.y() + off[1], m.z() + off[2],
                    m.x2() + off[0], m.y2() + off[1], m.z2() + off[2]);
        }
        if (ref.contains(":")) {
            String[] parts = ref.split(":");
            int[] a = parseTriple(parts[0]);
            int[] b = parseTriple(parts[1]);
            return Template.Marker.box(a[0], a[1], a[2], b[0], b[1], b[2]);
        }
        int[] p = parseTriple(ref);
        return Template.Marker.box(p[0], p[1], p[2], p[0], p[1], p[2]);
    }

    public static boolean exists(String ref, Template t) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        try {
            return !points(ref, t).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Template.Marker marker(String ref, Template t) {
        return t.marker(stripOffset(ref.substring(1)));
    }

    private static String stripOffset(String body) {
        int plus = body.indexOf('+');
        return plus < 0 ? body : body.substring(0, plus);
    }

    private static int[] offset(String ref) {
        int plus = ref.indexOf('+');
        if (plus < 0) {
            return new int[]{0, 0, 0};
        }
        return parseTriple(ref.substring(plus + 1));
    }

    public static int[] parseTriple(String s) {
        String[] parts = s.trim().split("\\s*,\\s*|\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected x,y,z but got '" + s + "'");
        }
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    /** Natural order: "lamp.2" < "lamp.10". */
    public static int natural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                long na = Long.parseLong(a.substring(si, i));
                long nb = Long.parseLong(b.substring(sj, j));
                if (na != nb) {
                    return Long.compare(na, nb);
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
