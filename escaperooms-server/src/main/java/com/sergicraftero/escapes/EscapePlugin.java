package com.sergicraftero.escapes;

import com.sergicraftero.escapes.command.AdminCommand;
import com.sergicraftero.escapes.command.PlayerCommands;
import com.sergicraftero.escapes.cosmetic.CosmeticService;
import com.sergicraftero.escapes.game.GameManager;
import com.sergicraftero.escapes.game.PartyManager;
import com.sergicraftero.escapes.gui.MenuListener;
import com.sergicraftero.escapes.gui.Menus;
import com.sergicraftero.escapes.hook.Bedrock;
import com.sergicraftero.escapes.instance.InstanceManager;
import com.sergicraftero.escapes.listener.PlayerListener;
import com.sergicraftero.escapes.listener.WorldListener;
import com.sergicraftero.escapes.lobby.Hotbar;
import com.sergicraftero.escapes.lobby.LobbyService;
import com.sergicraftero.escapes.pack.PackService;
import com.sergicraftero.escapes.profile.ProfileService;
import com.sergicraftero.escapes.profile.Storage;
import com.sergicraftero.escapes.progress.ProgressService;
import com.sergicraftero.escapes.room.AccessService;
import com.sergicraftero.escapes.room.ItemService;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.room.RoomRegistry;
import com.sergicraftero.escapes.shop.ShopService;
import com.sergicraftero.escapes.template.EditorService;
import com.sergicraftero.escapes.template.TemplateStore;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** EscapeRooms: a whole escape-room server (lobby, rooms, progression, shop) in one plugin. */
public class EscapePlugin extends JavaPlugin { // not final: MockBukkit subclasses it in tests

    private static EscapePlugin instance;

    private TemplateStore templates;
    private InstanceManager instances;
    private ItemService items;
    private RoomRegistry rooms;
    private AccessService access;
    private GameManager games;
    private PartyManager parties;
    private Storage storage;
    private ProfileService profiles;
    private ProgressService progress;
    private LobbyService lobby;
    private Hotbar hotbar;
    private Menus ui;
    private CosmeticService cosmetics;
    private ShopService shop;
    private PackService pack;
    private EditorService editor;
    private YamlConfiguration menus;
    private YamlConfiguration lobbyYml;
    private boolean started;

    public static EscapePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        saveDefaultConfig();
        for (String f : List.of("messages.yml", "menus.yml", "progression.yml", "cosmetics.yml", "lobby.yml")) {
            if (!new File(getDataFolder(), f).exists()) {
                saveResource(f, false);
            }
        }
        Bedrock.init();
        try {
            storage = new Storage(this);
            storage.open();
        } catch (Exception e) {
            getLogger().severe("Database error, disabling: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        templates = new TemplateStore(new File(getDataFolder(), "templates").toPath(), getLogger());
        items = new ItemService();
        rooms = new RoomRegistry(this);
        access = new AccessService(this);
        profiles = new ProfileService(this, storage);
        progress = new ProgressService(this);
        cosmetics = new CosmeticService(this);
        shop = new ShopService(this);
        pack = new PackService(this);
        hotbar = new Hotbar(this);
        ui = new Menus(this);
        parties = new PartyManager(this);
        lobby = new LobbyService(this);
        editor = new EditorService(this);
        loadFiles();

        instances = new InstanceManager(this, templates);
        instances.start();
        instances.assignRows(rooms.ids());
        games = new GameManager(this);
        lobby.start();
        pack.start();

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        PlayerCommands pc = new PlayerCommands(this);
        for (String c : List.of("menu", "salas", "jugar", "perfil", "tienda", "diario", "misiones", "cosmeticos", "top",
                "pista", "salir", "lobby", "grupo")) {
            register(c, pc);
        }
        register("eradmin", new AdminCommand(this));

        // validate rooms against their maps and warm up copies
        int warm = getConfig().getInt("instances.warm-per-room", 1);
        for (RoomDef r : rooms.all()) {
            List<String> problems = rooms.validate(r, templates.peek(r.template));
            if (!problems.isEmpty()) {
                getLogger().warning("Room " + r.id + " has " + problems.size() + " problem(s):");
                problems.forEach(s -> getLogger().warning("  - " + s));
            }
            if (r.enabled && warm > 0) {
                instances.warm(r, warm);
            }
        }
        storage.refreshAllAsync(rooms.ids());
        started = true;
        // players already online (plugin reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.preload(p.getUniqueId(), p.getName());
            profiles.joined(p);
            lobby.send(p);
        }
        getLogger().info("EscapeRooms ready: " + rooms.all().size() + " rooms.");
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command /" + name + " missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    private YamlConfiguration yaml(String name) {
        File f = new File(getDataFolder(), name);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        // fill keys added in newer versions without touching what the owner changed
        try (InputStream in = getResource(name)) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                y.setDefaults(def);
            }
        } catch (Exception ignored) {
            // defaults are optional
        }
        return y;
    }

    private void loadFiles() {
        reloadConfig();
        Msg.load(yaml("messages.yml"));
        menus = yaml("menus.yml");
        progress.load(yaml("progression.yml"));
        cosmetics.load(yaml("cosmetics.yml"));
        lobbyYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lobby.yml"));
        lobby.load(lobbyYml, new File(getDataFolder(), "lobby.yml"));
        rooms.load();
    }

    /** /eradmin reload: texts, menus, rooms, progression, cosmetics (maps stay; rebuild with /eradmin build). */
    public void reloadEverything() {
        loadFiles();
        templates.clearCache();
        instances.assignRows(rooms.ids());
        pack.start();
    }

    /** Next restart pastes the lobby map again (after rebuilding it). */
    public void markLobbyRepaste() {
        lobbyYml.set("force-paste", true);
        try {
            lobbyYml.save(new File(getDataFolder(), "lobby.yml"));
        } catch (java.io.IOException e) {
            getLogger().warning("Could not save lobby.yml: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (!started) {
            return;
        }
        games.shutdown();
        profiles.saveAllSync();
        pack.stop();
        instances.stop();
        storage.close();
    }

    // ───────────────────────── services ─────────────────────────

    public TemplateStore templates() {
        return templates;
    }

    public InstanceManager instances() {
        return instances;
    }

    public ItemService items() {
        return items;
    }

    public RoomRegistry rooms() {
        return rooms;
    }

    public AccessService access() {
        return access;
    }

    public GameManager games() {
        return games;
    }

    public PartyManager parties() {
        return parties;
    }

    public Storage storage() {
        return storage;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public ProgressService progress() {
        return progress;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public Hotbar hotbar() {
        return hotbar;
    }

    public Menus ui() {
        return ui;
    }

    public YamlConfiguration menus() {
        return menus;
    }

    public YamlConfiguration lobbyConfig() {
        return lobbyYml;
    }

    public CosmeticService cosmetics() {
        return cosmetics;
    }

    public ShopService shop() {
        return shop;
    }

    public PackService pack() {
        return pack;
    }

    public EditorService editor() {
        return editor;
    }
}
