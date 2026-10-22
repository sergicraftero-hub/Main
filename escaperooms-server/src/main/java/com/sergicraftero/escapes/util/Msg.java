package com.sergicraftero.escapes.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-facing text. Every string comes from {@code messages.yml} (MiniMessage) so the whole server can be
 * re-worded without touching code. Placeholders are passed as name/value pairs: {@code Msg.get("x", "room", name)}.
 */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();
    private static volatile String prefix = "";

    private Msg() {
    }

    public static void load(YamlConfiguration yml) {
        VALUES.clear();
        for (String key : yml.getKeys(true)) {
            if (yml.isConfigurationSection(key)) {
                continue;
            }
            if (yml.isList(key)) {
                VALUES.put(key, List.copyOf(yml.getStringList(key)));
            } else {
                VALUES.put(key, String.valueOf(yml.get(key)));
            }
        }
        prefix = raw("prefix");
    }

    /** The raw MiniMessage string, or a visible marker when the key is missing. */
    public static String raw(String key) {
        Object v = VALUES.get(key);
        if (v == null) {
            return "<red>[" + key + "]";
        }
        if (v instanceof List<?> list) {
            return String.join("\n", list.stream().map(String::valueOf).toList());
        }
        return (String) v;
    }

    public static boolean has(String key) {
        return VALUES.containsKey(key);
    }

    public static List<String> rawList(String key) {
        Object v = VALUES.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return v == null ? List.of() : List.of((String) v);
    }

    public static TagResolver resolver(Object... pairs) {
        List<TagResolver> list = new ArrayList<>();
        list.add(Placeholder.parsed("prefix", prefix));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            String name = String.valueOf(pairs[i]);
            if (value instanceof Component c) {
                list.add(Placeholder.component(name, c));
            } else {
                list.add(Placeholder.parsed(name, String.valueOf(value)));
            }
        }
        return TagResolver.resolver(list);
    }

    /** Parses an arbitrary MiniMessage string (room yml texts, menu names...). */
    public static Component parse(String text, Object... pairs) {
        if (text == null) {
            return Component.empty();
        }
        return MM.deserialize(text, resolver(pairs));
    }

    /** Parses for item names/lore: removes the default italic. */
    public static Component item(String text, Object... pairs) {
        return parse(text, pairs).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines, Object... pairs) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            for (String part : line.split("\n", -1)) {
                out.add(item(part, pairs));
            }
        }
        return out;
    }

    public static Component get(String key, Object... pairs) {
        return parse(raw(key), pairs);
    }

    public static List<Component> getLines(String key, Object... pairs) {
        return itemLines(rawList(key), pairs);
    }

    public static void send(Audience to, String key, Object... pairs) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        to.sendMessage(parse(raw, pairs));
    }

    public static void actionBar(Audience to, String key, Object... pairs) {
        to.sendActionBar(get(key, pairs));
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Strips MiniMessage tags from a string (for logs, sign fallbacks...). */
    public static String strip(String miniMessage) {
        return plain(parse(miniMessage));
    }

    public static String escape(String text) {
        return MM.escapeTags(text);
    }
}
