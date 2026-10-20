package com.sergicraftero.escaperoom.config;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads messages_<lang>.yml and renders MiniMessage strings with placeholders. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final EscapeRoomPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    public Messages(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String lang = plugin.getConfig().getString("language", "es");
        String fileName = "messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        if (!file.exists()) {
            fileName = "messages_es.yml";
            file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default messages: " + e.getMessage());
        }
    }

    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null) {
            value = defaults.getString(key, "<red>[missing message: " + key + "]");
        }
        return value;
    }

    public List<String> rawList(String key) {
        List<String> list = messages.getStringList(key);
        if (list.isEmpty()) {
            list = defaults.getStringList(key);
        }
        return list;
    }

    public Component get(String key, TagResolver... resolvers) {
        return parse(raw(key), resolvers);
    }

    public List<Component> getList(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(parse(line, resolvers));
        }
        return out;
    }

    public Component prefix() {
        return parse(raw("prefix"));
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        audience.sendMessage(prefix().append(parse(raw, resolvers)));
    }

    public void sendRaw(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(get(key, resolvers));
    }

    /** Parses an arbitrary MiniMessage string (used for room-defined texts). */
    public static Component parse(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage == null ? "" : miniMessage, TagResolver.resolver(resolvers));
    }

    /** Parses a string intended for item names / lore (no default italics). */
    public static Component item(String miniMessage, TagResolver... resolvers) {
        return parse(miniMessage, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static TagResolver ph(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver phc(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static TagResolver phm(String key, String miniMessage) {
        return Placeholder.parsed(key, miniMessage == null ? "" : miniMessage);
    }
}
