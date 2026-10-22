package com.sergicraftero.escapes.action;

import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Yml;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Builds {@link Action}s from YAML. Every action accepts {@code delay} (ticks, or "2s", "1m").
 * <p>
 * Types: message, actionbar, title, sound, particle, give, take, blocks, clear, open, close, light, unlight,
 * power, unpower, teleport, narrative, time, effect, spawn-prop, remove-prop, solve, checkpoint, command, firework,
 * lightning, player-time, win, lose.
 */
public final class Actions {

    private Actions() {
    }

    public static List<Action> parseList(ConfigurationSection parent, String key, Logger log, String context) {
        List<Action> out = new ArrayList<>();
        for (ConfigurationSection s : Yml.sections(parent, key)) {
            try {
                out.add(parse(s));
            } catch (RuntimeException e) {
                log.warning("[" + context + "] Ignoring action " + s.getValues(false) + ": " + e.getMessage());
            }
        }
        return out;
    }

    public static Action parse(ConfigurationSection s) {
        String type = s.getString("type", "").toLowerCase(Locale.ROOT);
        Action action = switch (type) {
            case "message", "msg" -> message(s);
            case "actionbar" -> actionBar(s);
            case "title" -> title(s);
            case "sound" -> sound(s);
            case "particle" -> particle(s);
            case "give" -> give(s);
            case "take" -> take(s);
            case "blocks", "set-blocks" -> blocks(s);
            case "clear" -> clear(s);
            case "open" -> openable(s, true);
            case "close" -> openable(s, false);
            case "light" -> lightable(s, true);
            case "unlight" -> lightable(s, false);
            case "power" -> powerable(s, true);
            case "unpower" -> powerable(s, false);
            case "teleport", "tp" -> teleport(s);
            case "narrative", "story" -> narrative(s);
            case "time" -> time(s);
            case "effect", "potion" -> effect(s);
            case "spawn-prop", "show" -> (g, p) -> g.props().spawn(s.getString("prop", s.getString("id", "")));
            case "remove-prop", "hide" -> (g, p) -> g.props().remove(s.getString("prop", s.getString("id", "")));
            case "solve" -> (g, p) -> g.solve(s.getString("puzzle", ""), p);
            case "checkpoint" -> (g, p) -> g.setCheckpoint(s.getString("at", s.getString("id", "")));
            case "command" -> command(s);
            case "firework" -> firework(s);
            case "lightning" -> lightning(s);
            case "player-time" -> playerTime(s);
            case "win" -> (g, p) -> g.win();
            case "lose" -> (g, p) -> g.lose(s.getString("reason", "game.lose-reason-story"));
            case "objective" -> (g, p) -> g.broadcastObjective(s.getString("text", ""));
            default -> throw new IllegalArgumentException("Unknown action type '" + type + "'");
        };
        long delay = Yml.ticks(s.get("delay"), 0);
        if (delay > 0) {
            Action inner = action;
            return (g, p) -> g.later(() -> inner.run(g, p), delay);
        }
        return action;
    }

    private static String name(Player p) {
        return p == null ? "?" : p.getName();
    }

    // ───────────────────────── feedback ─────────────────────────

    private static Action message(ConfigurationSection s) {
        List<String> lines = Yml.strings(s, "text");
        boolean toTrigger = "trigger".equalsIgnoreCase(s.getString("to", "all"));
        return (g, p) -> {
            for (String line : lines) {
                Component c = Msg.parse(line, "player", name(p));
                if (toTrigger && p != null) {
                    p.sendMessage(c);
                } else {
                    g.broadcast(c);
                }
            }
        };
    }

    private static Action actionBar(ConfigurationSection s) {
        String text = s.getString("text", "");
        return (g, p) -> {
            Component c = Msg.parse(text, "player", name(p));
            g.players().forEach(pl -> pl.sendActionBar(c));
        };
    }

    private static Action title(ConfigurationSection s) {
        String title = s.getString("title", "");
        String subtitle = s.getString("subtitle", "");
        Title.Times times = Title.Times.times(Duration.ofMillis(Yml.ticks(s.get("fade-in"), 10) * 50),
                Duration.ofMillis(Yml.ticks(s.get("stay"), 60) * 50), Duration.ofMillis(Yml.ticks(s.get("fade-out"), 20) * 50));
        return (g, p) -> {
            Title t = Title.title(Msg.parse(title, "player", name(p)), Msg.parse(subtitle, "player", name(p)), times);
            g.players().forEach(pl -> pl.showTitle(t));
        };
    }

    public static Sound sound(String key, float volume, float pitch) {
        return Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, volume, pitch);
    }

    private static Action sound(ConfigurationSection s) {
        Sound sound = sound(s.getString("sound", "block.note_block.pling"), (float) s.getDouble("volume", 1.0),
                (float) s.getDouble("pitch", 1.0));
        String at = s.getString("at");
        return (g, p) -> {
            if (at == null) {
                g.players().forEach(pl -> pl.playSound(sound, Sound.Emitter.self()));
            } else {
                Location l = g.center(at);
                g.players().forEach(pl -> pl.playSound(sound, l.getX(), l.getY(), l.getZ()));
            }
        };
    }

    private static Action particle(ConfigurationSection s) {
        Particle particle = particleType(s.getString("particle", "END_ROD"));
        String at = s.getString("at", "@spawn");
        int count = s.getInt("count", 20);
        double offset = s.getDouble("offset", 0.5);
        double speed = s.getDouble("speed", 0.02);
        String color = s.getString("color", "#FFFFFF");
        float size = (float) s.getDouble("size", 1.2);
        return (g, p) -> {
            for (Block b : g.blocks(at)) {
                spawnParticle(particle, b.getLocation().add(0.5, 0.5, 0.5), count, offset, speed, color, size);
            }
        };
    }

    public static Particle particleType(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.END_ROD;
        }
    }

    /** Spawns a particle supplying the data some types need (dust colour, block...). */
    public static void spawnParticle(Particle particle, Location at, int count, double offset, double speed,
                                     String hex, float size) {
        Class<?> type = particle.getDataType();
        Color color = Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        Object data = null;
        if (type == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, size);
        } else if (type == Color.class) {
            data = color;
        } else if (type == BlockData.class) {
            data = Material.STONE.createBlockData();
        } else if (type == org.bukkit.inventory.ItemStack.class) {
            data = new org.bukkit.inventory.ItemStack(Material.PAPER);
        } else if (type != Void.class) {
            return; // unsupported data type (vibration, trail...): skip rather than throw
        }
        if (data == null) {
            at.getWorld().spawnParticle(particle, at, count, offset, offset, offset, speed);
        } else {
            at.getWorld().spawnParticle(particle, at, count, offset, offset, offset, speed, data);
        }
    }

    // ───────────────────────── items ─────────────────────────

    private static Action give(ConfigurationSection s) {
        String item = s.getString("item", "");
        int amount = s.getInt("amount", 1);
        String to = s.getString("to", "trigger").toLowerCase(Locale.ROOT);
        return (g, p) -> {
            if (to.equals("all")) {
                g.players().forEach(pl -> g.giveItem(pl, item, amount));
            } else {
                Player target = p != null ? p : g.players().stream().findFirst().orElse(null);
                if (target != null) {
                    g.giveItem(target, item, amount);
                }
            }
        };
    }

    private static Action take(ConfigurationSection s) {
        String item = s.getString("item", "");
        int amount = s.getInt("amount", Integer.MAX_VALUE);
        return (g, p) -> g.takeItem(item, amount);
    }

    // ───────────────────────── blocks ─────────────────────────

    private static List<String> refs(ConfigurationSection s) {
        List<String> refs = new ArrayList<>(Yml.strings(s, "at"));
        if (refs.isEmpty()) {
            refs.addAll(Yml.strings(s, "region"));
        }
        return refs;
    }

    private static Action blocks(ConfigurationSection s) {
        List<String> refs = refs(s);
        String block = s.getString("block", "minecraft:stone");
        long interval = Yml.ticks(s.get("interval"), 0);
        boolean effect = s.getBoolean("effect", true);
        return (g, p) -> {
            BlockData data = Bukkit.createBlockData(block.contains(":") ? block : "minecraft:" + block.toLowerCase(Locale.ROOT));
            List<Block> blocks = new ArrayList<>();
            refs.forEach(r -> blocks.addAll(g.blocks(r)));
            placeOverTime(g, blocks, data, interval, effect);
        };
    }

    private static Action clear(ConfigurationSection s) {
        List<String> refs = refs(s);
        long interval = Yml.ticks(s.get("interval"), 0);
        boolean effect = s.getBoolean("effect", true);
        return (g, p) -> {
            List<Block> blocks = new ArrayList<>();
            refs.forEach(r -> blocks.addAll(g.blocks(r)));
            // top to bottom looks like a wall crumbling
            blocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
            placeOverTime(g, blocks, Material.AIR.createBlockData(), interval, effect);
        };
    }

    private static void placeOverTime(Game g, List<Block> blocks, BlockData data, long interval, boolean effect) {
        if (interval <= 0) {
            for (Block b : blocks) {
                place(b, data, effect);
            }
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            g.later(() -> place(b, data, effect), interval * i);
        }
    }

    private static void place(Block b, BlockData data, boolean effect) {
        if (effect) {
            BlockData old = b.getBlockData();
            if (!old.getMaterial().isAir()) {
                b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, old);
            }
            b.getWorld().playSound(b.getLocation(), data.getMaterial().isAir()
                    ? old.getSoundGroup().getBreakSound() : data.getSoundGroup().getPlaceSound(), 0.6f, 1f);
        }
        b.setBlockData(data, false);
    }

    private static Action openable(ConfigurationSection s, boolean open) {
        List<String> refs = refs(s);
        boolean quiet = s.getBoolean("quiet", false);
        return (g, p) -> {
            for (String r : refs) {
                for (Block b : g.blocks(r)) {
                    setOpen(b, open, quiet);
                }
            }
        };
    }

    public static void setOpen(Block b, boolean open, boolean quiet) {
        if (!(b.getBlockData() instanceof Openable o)) {
            return;
        }
        o.setOpen(open);
        b.setBlockData(o, false);
        if (o instanceof Door door) {
            // keep both halves in sync
            Block other = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? b.getRelative(0, 1, 0) : b.getRelative(0, -1, 0);
            if (other.getBlockData() instanceof Door od) {
                od.setOpen(open);
                other.setBlockData(od, false);
            }
        }
        if (!quiet) {
            org.bukkit.Sound snd = b.getType().name().contains("IRON")
                    ? (open ? org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN : org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE)
                    : (open ? org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN : org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE);
            b.getWorld().playSound(b.getLocation(), snd, 1f, 1f);
        }
    }

    private static Action lightable(ConfigurationSection s, boolean lit) {
        List<String> refs = refs(s);
        return (g, p) -> {
            for (String r : refs) {
                for (Block b : g.blocks(r)) {
                    setLit(b, lit);
                }
            }
        };
    }

    public static void setLit(Block b, boolean lit) {
        if (b.getBlockData() instanceof Lightable l) {
            l.setLit(lit);
            b.setBlockData(l, false);
        }
    }

    private static Action powerable(ConfigurationSection s, boolean powered) {
        List<String> refs = refs(s);
        return (g, p) -> {
            for (String r : refs) {
                for (Block b : g.blocks(r)) {
                    if (b.getBlockData() instanceof Powerable pw) {
                        pw.setPowered(powered);
                        b.setBlockData(pw, false);
                    }
                }
            }
        };
    }

    // ───────────────────────── players ─────────────────────────

    private static Action teleport(ConfigurationSection s) {
        String to = s.getString("to", "@spawn");
        boolean all = !"trigger".equalsIgnoreCase(s.getString("who", "all"));
        return (g, p) -> {
            Location l = g.standLocation(to);
            if (all || p == null) {
                g.players().forEach(pl -> pl.teleport(l));
            } else {
                p.teleport(l);
            }
        };
    }

    private static Action narrative(ConfigurationSection s) {
        List<String> lines = Yml.strings(s, "lines");
        long interval = Yml.ticks(s.get("interval"), 60);
        String style = s.getString("style", "chat").toLowerCase(Locale.ROOT);
        return (g, p) -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                g.later(() -> {
                    Component c = Msg.parse(line, "player", name(p));
                    if (style.equals("title")) {
                        Title t = Title.title(Component.empty(), c,
                                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(interval * 50 - 600), Duration.ofMillis(300)));
                        g.players().forEach(pl -> pl.showTitle(t));
                    } else if (style.equals("actionbar")) {
                        g.players().forEach(pl -> pl.sendActionBar(c));
                    } else {
                        g.broadcast(c);
                    }
                    g.players().forEach(pl -> pl.playSound(sound("ui.button.click", 0.3f, 1.6f), Sound.Emitter.self()));
                }, interval * i);
            }
        };
    }

    private static Action time(ConfigurationSection s) {
        long seconds = Yml.seconds(s.get("seconds"), 0);
        return (g, p) -> g.addPenalty(-seconds * 1000L);
    }

    private static Action effect(ConfigurationSection s) {
        String name = s.getString("effect", "blindness").toLowerCase(Locale.ROOT);
        int duration = (int) Yml.ticks(s.get("duration"), 60);
        int amplifier = s.getInt("amplifier", 0);
        boolean all = !"trigger".equalsIgnoreCase(s.getString("who", "all"));
        return (g, p) -> {
            PotionEffectType type = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(name));
            if (type == null) {
                return;
            }
            PotionEffect effect = new PotionEffect(type, duration, amplifier, true, false, false);
            if (all || p == null) {
                g.players().forEach(pl -> pl.addPotionEffect(effect));
            } else {
                p.addPotionEffect(effect);
            }
        };
    }

    private static Action playerTime(ConfigurationSection s) {
        long time = s.getLong("time", 18000);
        return (g, p) -> g.players().forEach(pl -> pl.setPlayerTime(time, false));
    }

    // ───────────────────────── world & server ─────────────────────────

    private static Action command(ConfigurationSection s) {
        String command = s.getString("command", "");
        boolean all = "all".equalsIgnoreCase(s.getString("for", "once"));
        return (g, p) -> {
            if (all) {
                for (Player pl : g.players()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", pl.getName()));
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", name(p)));
            }
        };
    }

    private static Action firework(ConfigurationSection s) {
        String at = s.getString("at", "@spawn");
        List<String> colors = Yml.strings(s, "colors");
        return (g, p) -> {
            Location l = g.center(at);
            Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
                FireworkMeta meta = f.getFireworkMeta();
                FireworkEffect.Builder b = FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).trail(true);
                if (colors.isEmpty()) {
                    b.withColor(Color.ORANGE, Color.YELLOW);
                } else {
                    colors.forEach(c -> b.withColor(Color.fromRGB(Integer.parseInt(c.replace("#", ""), 16))));
                }
                meta.addEffect(b.build());
                meta.setPower(0);
                f.setFireworkMeta(meta);
                g.tagEntity(f);
            });
            g.later(fw::detonate, 2);
        };
    }

    private static Action lightning(ConfigurationSection s) {
        String at = s.getString("at", "@spawn");
        // A real lightning bolt is heard across the whole world (other instances): fake it for this team only.
        return (g, p) -> {
            Location l = g.center(at);
            spawnParticle(Particle.FLASH, l, 3, 0.2, 0, "#FFFFFF", 1f);
            spawnParticle(Particle.ELECTRIC_SPARK, l, 60, 1.5, 0.3, "#FFFFFF", 1f);
            Sound thunder = sound("entity.lightning_bolt.thunder", 1f, 1f);
            g.players().forEach(pl -> pl.playSound(thunder, Sound.Emitter.self()));
        };
    }
}
