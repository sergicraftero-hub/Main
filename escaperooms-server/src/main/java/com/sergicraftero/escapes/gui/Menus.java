package com.sergicraftero.escapes.gui;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.game.PartyManager;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.profile.Storage;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.shop.ShopService;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Text;
import com.sergicraftero.escapes.util.Yml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every player menu. Layout of the main menu, wing icons and labels come from {@code menus.yml} and
 * {@code messages.yml}; the rest is generated from rooms, cosmetics and progression data.
 */
public final class Menus {

    private final EscapePlugin plugin;

    public Menus(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg(String path) {
        return plugin.menus().getConfigurationSection(path);
    }

    private ItemStack icon(String path, Object... pairs) {
        return ItemSpec.parse(null, cfg(path)).create(pairs);
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
    }

    /** Runs a menu action string: menu:x, wing:x, room:x, command:x, url:x, close. */
    public void action(Player p, String action) {
        if (action == null) {
            return;
        }
        String[] parts = action.split(":", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (parts[0]) {
            case "menu" -> open(p, arg);
            case "wing" -> wing(p, arg, 0);
            case "room" -> {
                RoomDef r = plugin.rooms().get(arg);
                if (r != null) {
                    room(p, r);
                }
            }
            case "command" -> p.performCommand(arg.replace("%player%", p.getName()));
            case "url" -> {
                p.closeInventory();
                p.sendMessage(Msg.get("menu.link", "url", arg).clickEvent(ClickEvent.openUrl(arg)));
            }
            case "close" -> p.closeInventory();
            default -> {
            }
        }
    }

    public void open(Player p, String id) {
        switch (id) {
            case "main" -> main(p);
            case "wings", "rooms" -> wings(p);
            case "profile" -> profile(p);
            case "achievements" -> achievements(p, 0);
            case "daily" -> daily(p);
            case "quests" -> quests(p);
            case "shop" -> shop(p);
            case "cosmetics" -> cosmetics(p);
            case "party" -> party(p);
            case "top", "leaderboards" -> leaderboards(p);
            case "settings" -> settings(p);
            case "notebook" -> notebook(p);
            default -> main(p);
        }
    }

    // ───────────────────────── main ─────────────────────────

    public void main(Player p) {
        ConfigurationSection m = cfg("main");
        Menu menu = new Menu(m == null ? 5 : m.getInt("rows", 5), MenuStyle.title(p, "main",
                Msg.parse(m == null ? "Menú" : m.getString("title", "Menú"))));
        PlayerProfile prof = plugin.profiles().get(p);
        int level = plugin.progress().level(prof.xp);
        Object[] ph = {"player", p.getName(), "level", level, "keys", Text.number(prof.keys), "gems", Text.number(prof.gems),
                "stars", prof.totalStars(), "rank", Msg.parse(plugin.progress().rankName(level)),
                "daily", plugin.progress().canClaimDaily(p) ? Msg.raw("menu.daily-ready") : Msg.raw("menu.daily-done")};
        ConfigurationSection items = m == null ? null : m.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                ItemStack it = ItemSpec.parse(null, s.getConfigurationSection("item")).create(ph);
                if (it.getType() == Material.PLAYER_HEAD && it.getItemMeta() instanceof SkullMeta sm) {
                    sm.setOwningPlayer(p);
                    it.setItemMeta(sm);
                }
                String action = s.getString("action", "close");
                menu.set(s.getInt("slot"), it, c -> {
                    click(p);
                    action(p, action);
                });
            }
        }
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    // ───────────────────────── rooms ─────────────────────────

    public void wings(Player p) {
        Menu menu = new Menu(4, MenuStyle.title(p, "rooms", Msg.get("menu.wings-title")));
        ConfigurationSection wings = plugin.getConfig().getConfigurationSection("wings");
        if (wings != null) {
            for (String id : wings.getKeys(false)) {
                ConfigurationSection w = wings.getConfigurationSection(id);
                List<RoomDef> rooms = plugin.rooms().wing(id);
                int done = 0;
                for (RoomDef r : rooms) {
                    if (plugin.profiles().get(p).completed(r.id)) {
                        done++;
                    }
                }
                ItemStack it = ItemSpec.parse(null, w.getConfigurationSection("icon")).create(
                        "rooms", rooms.size(), "done", done);
                appendLore(it, Msg.getLines("menu.wing-lore", "rooms", rooms.size(), "done", done));
                menu.set(w.getInt("slot", 10), it, c -> {
                    click(p);
                    wing(p, id, 0);
                });
            }
        }
        menu.set(31, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void wing(Player p, String wing, int page) {
        List<RoomDef> rooms = plugin.rooms().wing(wing);
        String wingName = plugin.getConfig().getString("wings." + wing + ".name", wing);
        Menu menu = new Menu(6, MenuStyle.title(p, "rooms", Msg.parse(wingName)));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int per = slots.length;
        int from = page * per;
        for (int i = 0; i < per && from + i < rooms.size(); i++) {
            RoomDef r = rooms.get(from + i);
            menu.set(slots[i], roomIcon(p, r), c -> {
                click(p);
                room(p, r);
            });
        }
        if (rooms.isEmpty()) {
            menu.set(22, MenuStyle.button(Material.BARRIER, Msg.raw("menu.wing-empty")));
        }
        if (page > 0) {
            menu.set(45, MenuStyle.button(Material.ARROW, Msg.raw("menu.previous")), c -> wing(p, wing, page - 1));
        }
        if (from + per < rooms.size()) {
            menu.set(53, MenuStyle.button(Material.ARROW, Msg.raw("menu.next")), c -> wing(p, wing, page + 1));
        }
        menu.set(49, MenuStyle.back(), c -> wings(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    /** Room icon with its status (locked/premium/stars/best time). */
    public ItemStack roomIcon(Player p, RoomDef r) {
        PlayerProfile prof = plugin.profiles().get(p);
        PlayerProfile.RoomRecord rec = prof.rooms.get(r.id);
        String deny = plugin.access().denyReason(p, r);
        String status;
        if (deny == null) {
            status = Msg.raw(r.isPaid() ? "menu.room-status-owned" : "menu.room-status-open");
        } else {
            status = Msg.raw(deny.equals("room.locked-premium") ? "menu.room-status-premium" : "menu.room-status-locked");
        }
        String diff = plugin.getConfig().getString("difficulties." + r.difficulty, r.difficulty);
        ItemSpec spec = deny != null && !deny.equals("room.locked-premium") && plugin.getConfig().getBoolean("menus.grey-locked-rooms", true)
                ? r.icon.withMaterial(Material.GRAY_DYE) : r.icon;
        ItemStack it = spec.withName(r.name).withLore(List.of()).create();
        List<Component> lore = new ArrayList<>(Msg.itemLines(r.description));
        lore.add(Component.empty());
        lore.addAll(Msg.getLines("menu.room-lore", "difficulty", Msg.parse(diff), "min", r.minPlayers, "max", r.maxPlayers,
                "minutes", r.estimatedMinutes, "puzzles", r.mainPuzzles().size(),
                "stars", Text.stars(rec == null ? 0 : rec.bestStars, 3),
                "best", rec == null || rec.bestTimeMs == 0 ? "—" : Text.time(rec.bestTimeMs),
                "status", Msg.parse(status, "level", r.unlockLevel, "gems", r.priceGems, "keys", r.priceKeys),
                "queue", plugin.games().queued(r), "playing", plugin.games().playing(r)));
        ItemMeta meta = it.getItemMeta();
        meta.lore(lore);
        if (rec != null && rec.bestStars >= 3) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    public void room(Player p, RoomDef r) {
        Menu menu = new Menu(5, MenuStyle.title(p, "room", Msg.parse(r.name)));
        menu.set(4, roomIcon(p, r));
        String deny = plugin.access().denyReason(p, r);
        PartyManager.Party party = plugin.parties().of(p);
        if (deny == null) {
            String playKey = party == null ? "menu.play-solo" : "menu.play-party";
            menu.set(20, MenuStyle.button(Material.LIME_CONCRETE, Msg.raw(playKey),
                    Msg.rawList(playKey + "-lore")), c -> {
                p.closeInventory();
                plugin.games().playPrivate(p, r);
            });
            if (r.maxPlayers > 1) {
                menu.set(22, MenuStyle.button(Material.CYAN_CONCRETE, Msg.raw("menu.play-public"),
                        Msg.rawList("menu.play-public-lore")), c -> {
                    p.closeInventory();
                    plugin.games().joinQueue(p, r);
                });
            }
        } else if (deny.equals("room.locked-premium")) {
            if (r.priceGems > 0) {
                menu.set(20, MenuStyle.button(Material.AMETHYST_SHARD, Msg.raw("menu.buy-gems").replace("<price>", String.valueOf(r.priceGems))),
                        c -> confirm(p, Msg.parse(r.name), r.priceGems + " 💎", () -> {
                            plugin.shop().buyRoom(p, r, ShopService.Currency.GEMS);
                            room(p, r);
                        }, () -> room(p, r)));
            }
            if (r.priceKeys > 0) {
                menu.set(22, MenuStyle.button(Material.TRIPWIRE_HOOK, Msg.raw("menu.buy-keys").replace("<price>", String.valueOf(r.priceKeys))),
                        c -> confirm(p, Msg.parse(r.name), r.priceKeys + " 🗝", () -> {
                            plugin.shop().buyRoom(p, r, ShopService.Currency.KEYS);
                            room(p, r);
                        }, () -> room(p, r)));
            }
            menu.set(24, MenuStyle.button(Material.EMERALD, Msg.raw("menu.store-link")), c -> action(p, "url:" + plugin.shop().storeUrl()));
        } else {
            menu.set(22, MenuStyle.button(Material.BARRIER, Msg.raw("menu.room-locked"),
                    List.of(Msg.raw(deny).replace("<level>", String.valueOf(r.unlockLevel)).replace("<room>", r.name).replace("<player>", p.getName()))));
        }
        if (deny == null) {
            menu.set(24, MenuStyle.button(Material.GOLD_INGOT, Msg.raw("menu.room-top")), c -> roomTop(p, r));
        } else {
            menu.set(31, MenuStyle.button(Material.GOLD_INGOT, Msg.raw("menu.room-top")), c -> roomTop(p, r));
        }
        menu.set(40, MenuStyle.back(), c -> wing(p, r.wing, 0));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void roomTop(Player p, RoomDef r) {
        Menu menu = new Menu(4, MenuStyle.title(p, "generic", Msg.get("menu.top-title", "room", Msg.parse(r.name))));
        List<Storage.Run> all = plugin.storage().best(r.id);
        List<Storage.Run> week = plugin.storage().weekly(r.id);
        menu.set(11, runsItem(Material.GOLD_BLOCK, Msg.raw("menu.top-all"), all));
        menu.set(15, runsItem(Material.CLOCK, Msg.raw("menu.top-week"), week));
        menu.set(31, MenuStyle.back(), c -> room(p, r));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private ItemStack runsItem(Material m, String title, List<Storage.Run> runs) {
        List<String> lore = new ArrayList<>();
        int i = 1;
        for (Storage.Run run : runs) {
            lore.add(Msg.raw("menu.top-line").replace("<pos>", String.valueOf(i++)).replace("<time>", Text.time(run.timeMs()))
                    .replace("<players>", Msg.escape(run.players())).replace("<stars>", Text.stars(run.stars(), 3)));
        }
        if (runs.isEmpty()) {
            lore.add(Msg.raw("lobby.top-empty"));
        }
        return MenuStyle.button(m, title, lore);
    }

    // ───────────────────────── profile ─────────────────────────

    public void profile(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        int level = plugin.progress().level(prof.xp);
        Menu menu = new Menu(5, MenuStyle.title(p, "profile", Msg.get("menu.profile-title", "player", p.getName())));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Msg.item("<gold>" + p.getName()));
        long next = plugin.progress().xpFor(level + 1);
        sm.lore(Msg.getLines("menu.profile-lore", "level", level, "rank", Msg.parse(plugin.progress().rankName(level)),
                "xp", Text.number(prof.xp), "next", Text.number(next),
                "bar", Text.bar(plugin.progress().levelProgress(prof.xp), 20, "<green>|", "<dark_gray>|"),
                "keys", Text.number(prof.keys), "gems", Text.number(prof.gems)));
        head.setItemMeta(sm);
        menu.set(13, head);
        menu.set(29, MenuStyle.button(Material.BOOK, Msg.raw("menu.stats"), Msg.rawList("menu.stats-lore").stream()
                .map(l -> l.replace("<wins>", String.valueOf(prof.stat("wins"))).replace("<plays>", String.valueOf(prof.stat("plays")))
                        .replace("<puzzles>", String.valueOf(prof.stat("puzzles"))).replace("<stars>", String.valueOf(prof.totalStars()))
                        .replace("<rooms>", String.valueOf(prof.roomsCompleted())).replace("<total>", String.valueOf(plugin.rooms().all().size()))
                        .replace("<playtime>", Text.duration(prof.playtimeMs)).replace("<streak>", String.valueOf(prof.dailyStreak))
                        .replace("<three>", String.valueOf(prof.stat("three_stars"))))
                .toList()));
        menu.set(31, MenuStyle.button(Material.NETHER_STAR, Msg.raw("menu.achievements"),
                List.of(Msg.raw("menu.achievements-count").replace("<done>", String.valueOf(prof.achievements.size()))
                        .replace("<total>", String.valueOf(achievementCount())))), c -> achievements(p, 0));
        menu.set(33, MenuStyle.button(Material.COMPARATOR, Msg.raw("menu.settings")), c -> settings(p));
        menu.set(40, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private int achievementCount() {
        ConfigurationSection a = plugin.progress().yml().getConfigurationSection("achievements");
        return a == null ? 0 : a.getKeys(false).size();
    }

    public void achievements(Player p, int page) {
        ConfigurationSection all = plugin.progress().yml().getConfigurationSection("achievements");
        List<String> ids = all == null ? List.of() : new ArrayList<>(all.getKeys(false));
        Menu menu = new Menu(6, MenuStyle.title(p, "generic", Msg.get("menu.achievements-title")));
        PlayerProfile prof = plugin.profiles().get(p);
        int per = 45;
        for (int i = 0; i < per && page * per + i < ids.size(); i++) {
            String id = ids.get(page * per + i);
            ConfigurationSection a = all.getConfigurationSection(id);
            boolean done = prof.achievements.contains(id);
            long value = plugin.progress().statValue(p, a.getString("stat", "wins"));
            long amount = a.getLong("amount", 1);
            List<String> lore = new ArrayList<>();
            lore.add(a.getString("description", ""));
            lore.add("");
            lore.add(done ? Msg.raw("menu.achievement-done") : Msg.raw("menu.achievement-progress")
                    .replace("<value>", String.valueOf(Math.min(value, amount))).replace("<amount>", String.valueOf(amount)));
            ItemStack it = MenuStyle.button(done ? ItemSpec.material(a.getString("icon", "DIAMOND")) : Material.GRAY_DYE,
                    (done ? "<green>" : "<gray>") + a.getString("name", id), lore);
            menu.set(i, it);
        }
        if (page > 0) {
            menu.set(45, MenuStyle.button(Material.ARROW, Msg.raw("menu.previous")), c -> achievements(p, page - 1));
        }
        if ((page + 1) * per < ids.size()) {
            menu.set(53, MenuStyle.button(Material.ARROW, Msg.raw("menu.next")), c -> achievements(p, page + 1));
        }
        menu.set(49, MenuStyle.back(), c -> profile(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void settings(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        Menu menu = new Menu(3, MenuStyle.title(p, "generic", Msg.get("menu.settings-title")));
        boolean hide = prof.setting("hide-players", false);
        menu.set(11, MenuStyle.button(hide ? Material.ENDER_EYE : Material.ENDER_PEARL,
                Msg.raw(hide ? "menu.setting-players-hidden" : "menu.setting-players-shown")), c -> {
            prof.settings.put("hide-players", !hide);
            prof.dirty = true;
            plugin.lobby().updateVisibility(p);
            settings(p);
        });
        boolean pings = prof.setting("party-sounds", true);
        menu.set(15, MenuStyle.button(pings ? Material.NOTE_BLOCK : Material.JUKEBOX,
                Msg.raw(pings ? "menu.setting-sounds-on" : "menu.setting-sounds-off")), c -> {
            prof.settings.put("party-sounds", !pings);
            prof.dirty = true;
            settings(p);
        });
        menu.set(22, MenuStyle.back(), c -> profile(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    // ───────────────────────── daily & quests ─────────────────────────

    public void daily(Player p) {
        Menu menu = new Menu(3, MenuStyle.title(p, "generic", Msg.get("menu.daily-title")));
        List<Map<?, ?>> rewards = plugin.progress().dailyRewards();
        int next = plugin.progress().nextStreakDay(p);
        boolean can = plugin.progress().canClaimDaily(p);
        int cycle = Math.max(1, rewards.size());
        int base = ((next - 1) / cycle) * cycle;
        for (int i = 0; i < Math.min(7, rewards.size()); i++) {
            ConfigurationSection r = Yml.section(rewards.get(i));
            int day = base + i + 1;
            boolean claimed = day < next || (day == next && !can);
            boolean today = day == next && can;
            Material m = claimed ? Material.MINECART : today ? Material.CHEST_MINECART : Material.CHEST;
            List<String> lore = new ArrayList<>();
            if (r.getLong("keys", 0) > 0) {
                lore.add("<gray>+ <gold>" + r.getLong("keys") + " 🗝");
            }
            if (r.getLong("gems", 0) > 0) {
                lore.add("<gray>+ <light_purple>" + r.getLong("gems") + " 💎");
            }
            if (r.getLong("xp", 0) > 0) {
                lore.add("<gray>+ <aqua>" + r.getLong("xp") + " XP");
            }
            lore.add("");
            lore.add(Msg.raw(claimed ? "menu.daily-claimed" : today ? "menu.daily-claim" : "menu.daily-locked"));
            ItemStack it = MenuStyle.button(m, Msg.raw("menu.daily-day").replace("<day>", String.valueOf(day)), lore);
            if (today) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            menu.set(10 + i, it, today ? c -> {
                plugin.progress().claimDaily(p);
                daily(p);
            } : null);
        }
        menu.set(22, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void quests(Player p) {
        plugin.progress().ensureQuests(p);
        PlayerProfile prof = plugin.profiles().get(p);
        Menu menu = new Menu(4, MenuStyle.title(p, "generic", Msg.get("menu.quests-title")));
        menu.set(1, MenuStyle.button(Material.SUNFLOWER, Msg.raw("menu.quests-daily")));
        menu.set(19, MenuStyle.button(Material.CLOCK, Msg.raw("menu.quests-weekly")));
        questRow(p, menu, true, prof.dailyQuests, 3);
        questRow(p, menu, false, prof.weeklyQuests, 21);
        menu.set(31, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private void questRow(Player p, Menu menu, boolean daily, List<String> ids, int startSlot) {
        for (int i = 0; i < ids.size() && i < 5; i++) {
            String id = ids.get(i);
            ConfigurationSection q = plugin.progress().questDef(daily, id);
            if (q == null) {
                continue;
            }
            int progress = plugin.progress().questProgress(p, daily, id);
            int amount = q.getInt("amount", 1);
            boolean done = progress >= amount;
            boolean claimed = plugin.progress().questClaimed(p, daily, id);
            List<String> lore = new ArrayList<>();
            lore.add(q.getString("description", ""));
            lore.add("");
            lore.add("<gray>" + Text.bar((double) progress / amount, 15, "<green>|", "<dark_gray>|") + " <white>" + progress + "/" + amount);
            lore.add(Msg.raw("menu.quest-reward").replace("<xp>", String.valueOf(q.getLong("reward.xp", 0)))
                    .replace("<keys>", String.valueOf(q.getLong("reward.keys", 0))));
            lore.add("");
            lore.add(Msg.raw(claimed ? "menu.quest-claimed" : done ? "menu.quest-claim" : "menu.quest-in-progress"));
            Material m = claimed ? Material.LIME_DYE : done ? Material.GOLD_INGOT : Material.PAPER;
            menu.set(startSlot + i, MenuStyle.button(m, q.getString("name", id), lore), done && !claimed ? c -> {
                plugin.progress().claimQuest(p, daily, id);
                quests(p);
            } : null);
        }
    }

    // ───────────────────────── shop & cosmetics ─────────────────────────

    public void shop(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        Menu menu = new Menu(5, MenuStyle.title(p, "shop", Msg.get("menu.shop-title")));
        menu.set(4, MenuStyle.button(Material.SUNFLOWER, Msg.raw("menu.balance"), List.of(
                "<gold>🗝 " + Text.number(prof.keys), "<light_purple>💎 " + Text.number(prof.gems))));
        menu.set(20, MenuStyle.button(Material.FILLED_MAP, Msg.raw("menu.shop-rooms"), Msg.rawList("menu.shop-rooms-lore")),
                c -> premiumRooms(p));
        menu.set(22, MenuStyle.button(Material.ARMOR_STAND, Msg.raw("menu.shop-cosmetics"), Msg.rawList("menu.shop-cosmetics-lore")),
                c -> cosmetics(p));
        ConfigurationSection ranks = plugin.menus().getConfigurationSection("shop.ranks");
        menu.set(24, MenuStyle.button(Material.DIAMOND, Msg.raw("menu.shop-ranks"),
                ranks == null ? List.of() : ranks.getStringList("lore")), c -> action(p, "url:" + plugin.shop().storeUrl()));
        menu.set(31, MenuStyle.button(Material.AMETHYST_CLUSTER, Msg.raw("menu.shop-gems"), Msg.rawList("menu.shop-gems-lore")),
                c -> action(p, "url:" + plugin.shop().storeUrl()));
        menu.set(40, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void premiumRooms(Player p) {
        Menu menu = new Menu(6, MenuStyle.title(p, "shop", Msg.get("menu.shop-rooms")));
        int slot = 10;
        for (RoomDef r : plugin.rooms().all()) {
            if (!r.isPaid()) {
                continue;
            }
            menu.set(slot, roomIcon(p, r), c -> room(p, r));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 44) {
                break;
            }
        }
        menu.set(49, MenuStyle.back(), c -> shop(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void cosmetics(Player p) {
        Menu menu = new Menu(3, MenuStyle.title(p, "shop", Msg.get("menu.cosmetics-title")));
        Material[] icons = {Material.NAME_TAG, Material.BLAZE_POWDER, Material.LEATHER_HELMET, Material.FIREWORK_ROCKET};
        for (int i = 0; i < 4; i++) {
            String type = com.sergicraftero.escapes.cosmetic.CosmeticService.TYPES.get(i);
            menu.set(10 + i * 2, MenuStyle.button(icons[i], Msg.raw("cosmetics." + type)), c -> cosmeticType(p, type));
        }
        menu.set(22, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    public void cosmeticType(Player p, String type) {
        Menu menu = new Menu(6, MenuStyle.title(p, "shop", Msg.parse(Msg.raw("cosmetics." + type))));
        String equipped = plugin.cosmetics().equipped(p, type);
        int slot = 0;
        for (String id : plugin.cosmetics().ids(type)) {
            if (slot >= 45) {
                break;
            }
            ConfigurationSection c = plugin.cosmetics().get(type, id);
            boolean owns = plugin.cosmetics().owns(p, type, id);
            long[] price = plugin.cosmetics().price(type, id);
            List<String> lore = new ArrayList<>(c.getStringList("description"));
            if (type.equals("titles")) {
                lore.add("<gray>" + Msg.raw("cosmetics.preview") + " " + c.getString("display", ""));
            }
            lore.add("");
            if (id.equals(equipped)) {
                lore.add(Msg.raw("cosmetics.equipped"));
            } else if (owns) {
                lore.add(Msg.raw("cosmetics.click-equip"));
            } else if (price[0] > 0 || price[1] > 0) {
                if (price[0] > 0) {
                    lore.add(Msg.raw("cosmetics.price-keys").replace("<price>", String.valueOf(price[0])));
                }
                if (price[1] > 0) {
                    lore.add(Msg.raw("cosmetics.price-gems").replace("<price>", String.valueOf(price[1])));
                }
            } else {
                lore.add(Msg.raw("cosmetics.unlock-other").replace("<how>", c.getString("unlock", "")));
            }
            Material m = ItemSpec.material(c.getString("icon", "PAPER"));
            ItemStack it = MenuStyle.button(owns ? m : Material.GRAY_DYE, c.getString("name", id), lore);
            if (id.equals(equipped)) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            menu.set(slot++, it, click -> {
                if (id.equals(equipped)) {
                    plugin.cosmetics().equip(p, type, null);
                } else if (owns) {
                    plugin.cosmetics().equip(p, type, id);
                } else if (price[1] > 0 && (click.right() || price[0] <= 0)) {
                    confirm(p, Msg.parse(c.getString("name", id)), price[1] + " 💎",
                            () -> plugin.shop().buyCosmetic(p, type, id, ShopService.Currency.GEMS), () -> cosmeticType(p, type));
                    return;
                } else if (price[0] > 0) {
                    confirm(p, Msg.parse(c.getString("name", id)), price[0] + " 🗝",
                            () -> plugin.shop().buyCosmetic(p, type, id, ShopService.Currency.KEYS), () -> cosmeticType(p, type));
                    return;
                } else {
                    return;
                }
                cosmeticType(p, type);
            });
        }
        menu.set(49, MenuStyle.back(), c -> cosmetics(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    /** Yes/no purchase confirmation. */
    public void confirm(Player p, Component what, String price, Runnable yes, Runnable back) {
        Menu menu = new Menu(3, MenuStyle.title(p, "generic", Msg.get("menu.confirm-title")));
        menu.set(11, MenuStyle.button(Material.LIME_CONCRETE, Msg.raw("menu.confirm-yes"),
                List.of(Msg.raw("menu.confirm-what").replace("<price>", price))), c -> {
            yes.run();
            if (p.getOpenInventory().getTopInventory().getHolder() == menu) {
                p.closeInventory();
            }
        });
        ItemStack info = MenuStyle.button(Material.PAPER, "<white>");
        ItemMeta meta = info.getItemMeta();
        meta.displayName(what);
        info.setItemMeta(meta);
        menu.set(13, info);
        menu.set(15, MenuStyle.button(Material.RED_CONCRETE, Msg.raw("menu.confirm-no")), c -> back.run());
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    // ───────────────────────── party ─────────────────────────

    public void party(Player p) {
        PartyManager.Party party = plugin.parties().of(p);
        Menu menu = new Menu(4, MenuStyle.title(p, "generic", Msg.get("menu.party-title")));
        if (party == null) {
            menu.set(13, MenuStyle.button(Material.WRITABLE_BOOK, Msg.raw("menu.party-none"), Msg.rawList("menu.party-none-lore")));
        } else {
            int slot = 9;
            for (UUID u : party.members()) {
                Player m = Bukkit.getPlayer(u);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(u));
                String name = m != null ? m.getName() : Bukkit.getOfflinePlayer(u).getName();
                sm.displayName(Msg.item((u.equals(party.leader()) ? "<gold>★ " : "<white>") + name));
                sm.lore(List.of(Msg.item(m == null ? "<red>Desconectado" : "<green>Conectado")));
                head.setItemMeta(sm);
                boolean kickable = party.isLeader(p) && !u.equals(p.getUniqueId());
                menu.set(slot++, head, kickable ? c -> {
                    plugin.parties().kick(p, name);
                    party(p);
                } : null);
            }
            menu.set(31, MenuStyle.button(Material.RED_BED, Msg.raw("menu.party-leave")), c -> {
                plugin.parties().leave(p, true);
                p.closeInventory();
            });
        }
        menu.set(27, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    // ───────────────────────── leaderboards ─────────────────────────

    public void leaderboards(Player p) {
        Menu menu = new Menu(6, MenuStyle.title(p, "generic", Msg.get("menu.leaderboards-title")));
        List<String> lore = new ArrayList<>();
        int i = 1;
        for (Storage.LevelEntry e : plugin.storage().topLevels()) {
            lore.add("<yellow>#" + i++ + " <white>" + e.name() + " <gray>- <aqua>Nv. " + plugin.progress().level(e.xp()));
        }
        menu.set(4, MenuStyle.button(Material.EXPERIENCE_BOTTLE, Msg.raw("menu.top-levels"), lore));
        int slot = 18;
        for (RoomDef r : plugin.rooms().all()) {
            if (slot >= 45) {
                break;
            }
            ItemStack it = runsItem(r.icon.material(), r.name, plugin.storage().best(r.id));
            menu.set(slot++, it, c -> roomTop(p, r));
        }
        menu.set(49, MenuStyle.back(), c -> main(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    // ───────────────────────── in-game notebook ─────────────────────────

    public void notebook(Player p) {
        Game g = plugin.games().of(p);
        if (g == null) {
            main(p);
            return;
        }
        Menu menu = new Menu(6, MenuStyle.title(p, "notebook", Msg.get("notebook.title", "room", Msg.parse(g.room().name))));
        menu.set(4, MenuStyle.button(Material.CLOCK, Msg.raw("notebook.info"), Msg.rawList("notebook.info-lore").stream()
                .map(l -> l.replace("<time>", Text.time(Math.max(0, g.room().timeLimitMs - g.elapsed())))
                        .replace("<solved>", String.valueOf(g.solvedCount()))
                        .replace("<total>", String.valueOf(g.room().mainPuzzles().size()))
                        .replace("<hints>", String.valueOf(g.hintsUsed()))
                        .replace("<max>", String.valueOf(g.room().maxHints))
                        .replace("<penalty>", String.valueOf(g.room().hintPenaltyMs / 1000))).toList()));
        int slot = 18;
        for (Puzzle pz : g.objectives()) {
            if (slot >= 45) {
                break;
            }
            int level = g.hintLevel(pz.id());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + pz.def().objective());
            double prog = pz.progress();
            if (prog >= 0) {
                lore.add("<gray>" + Text.bar(prog, 12, "<green>|", "<dark_gray>|"));
            }
            lore.add("");
            for (int i = 0; i < level && i < pz.def().hints().size(); i++) {
                lore.add("<yellow>💡 " + pz.def().hints().get(i));
            }
            if (level < pz.def().hints().size()) {
                lore.add(Msg.raw("notebook.click-hint").replace("<n>", String.valueOf(level + 1))
                        .replace("<max>", String.valueOf(pz.def().hints().size()))
                        .replace("<penalty>", String.valueOf(g.room().hintPenaltyMs / 1000)));
            } else if (!pz.def().hints().isEmpty()) {
                lore.add(Msg.raw("notebook.no-more-hints"));
            }
            Consumer<Menu.Click> onClick = level < pz.def().hints().size() ? c -> {
                g.useHint(p, pz.id());
                notebook(p);
            } : null;
            menu.set(slot++, MenuStyle.button(Material.MAP, "<yellow>" + pz.def().name(), lore), onClick);
        }
        if (g.objectives().isEmpty()) {
            menu.set(22, MenuStyle.button(Material.COMPASS, Msg.raw("notebook.explore")));
        }
        menu.set(49, MenuStyle.button(Material.OAK_DOOR, Msg.raw("notebook.leave"), Msg.rawList("notebook.leave-lore")), c -> {
            confirmLeave(p, g);
        });
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private void confirmLeave(Player p, Game g) {
        Menu menu = new Menu(3, MenuStyle.title(p, "generic", Msg.get("notebook.leave-confirm")));
        menu.set(11, MenuStyle.button(Material.RED_CONCRETE, Msg.raw("notebook.leave-yes")), c -> {
            p.closeInventory();
            g.leave(p, true);
        });
        menu.set(15, MenuStyle.button(Material.LIME_CONCRETE, Msg.raw("notebook.leave-no")), c -> notebook(p));
        menu.fill(MenuStyle.filler());
        menu.open(p);
    }

    private static void appendLore(ItemStack it, List<Component> extra) {
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.addAll(extra);
        meta.lore(lore);
        it.setItemMeta(meta);
    }
}
