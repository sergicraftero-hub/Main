package com.sergicraftero.escapes.pack;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.hook.Bedrock;
import com.sergicraftero.escapes.util.Msg;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Sends the server resource pack to Java players. Either serves the pack bundled in the plugin with a tiny
 * built-in web server, or uses an external URL (recommended for big servers / CDN). Bedrock players never get
 * the Java pack; everything keeps a vanilla look for them.
 */
public final class PackService {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("escaperooms-pack".getBytes());

    private final EscapePlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private HttpServer server;
    private String url;
    private byte[] hash;
    private boolean enabled;

    public PackService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("resource-pack");
        enabled = c != null && c.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }
        try {
            if (c.getString("mode", "builtin").equalsIgnoreCase("url")) {
                url = c.getString("url", "");
                String sha1 = c.getString("sha1", "");
                hash = sha1.isEmpty() ? null : HexFormat.of().parseHex(sha1);
            } else {
                Path zip = plugin.getDataFolder().toPath().resolve("escaperooms-pack.zip");
                try (InputStream in = plugin.getResource("escaperooms-pack.zip")) {
                    if (in == null) {
                        plugin.getLogger().warning("Resource pack missing from the jar; disabled.");
                        enabled = false;
                        return;
                    }
                    Files.copy(in, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                // A custom pack dropped in the folder replaces the bundled one.
                Path custom = plugin.getDataFolder().toPath().resolve("custom-pack.zip");
                Path served = Files.exists(custom) ? custom : zip;
                byte[] bytes = Files.readAllBytes(served);
                hash = MessageDigest.getInstance("SHA-1").digest(bytes);
                int port = c.getInt("port", 8163);
                String host = c.getString("public-host", "");
                if (host.isEmpty()) {
                    host = plugin.getServer().getIp().isEmpty() ? "localhost" : plugin.getServer().getIp();
                    plugin.getLogger().warning("resource-pack.public-host is empty: players outside this machine "
                            + "won't download the pack. Set it to your server's public IP or domain.");
                }
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/pack.zip", ex -> {
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.sendResponseHeaders(200, bytes.length);
                    try (OutputStream out = ex.getResponseBody()) {
                        out.write(bytes);
                    }
                });
                server.setExecutor(Executors.newFixedThreadPool(2));
                server.start();
                url = "http://" + host + ":" + port + "/pack.zip?v=" + HexFormat.of().formatHex(hash).substring(0, 8);
                plugin.getLogger().info("Serving resource pack at " + url);
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            plugin.getLogger().severe("Resource pack disabled: " + e.getMessage());
            enabled = false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void send(Player p) {
        if (!enabled || url == null || url.isEmpty() || Bedrock.is(p)) {
            return;
        }
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("resource-pack");
        boolean required = c != null && c.getBoolean("required", false);
        p.setResourcePack(PACK_ID, url, hash, Msg.get("pack.prompt"), required);
    }

    public void status(Player p, PlayerResourcePackStatusEvent.Status status) {
        switch (status) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case DECLINED -> Msg.send(p, "pack.declined");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                loaded.remove(p.getUniqueId());
                Msg.send(p, "pack.failed");
            }
            default -> {
            }
        }
    }

    public boolean hasPack(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    public void quit(Player p) {
        loaded.remove(p.getUniqueId());
    }
}
