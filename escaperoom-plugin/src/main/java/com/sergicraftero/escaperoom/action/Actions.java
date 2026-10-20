package com.sergicraftero.escaperoom.action;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.room.ItemSpec;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Cfg;
import com.sergicraftero.escaperoom.util.Cuboid;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Factory for the built-in action types. */
public final class Actions {

    private Actions() {
    }

    public static RoomAction parse(ConfigurationSection s) {
        String type = s.getString("type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "message" -> message(s);
            case "actionbar" -> actionBar(s);
            case "title" -> title(s);
            case "sound" -> sound(s);
            case "particle" -> particle(s);
            case "give" -> give(s);
            case "set-blocks" -> setBlocks(s);
            case "open" -> open(s);
            case "light" -> light(s);
            case "redstone" -> redstone(s);
            case "slide" -> slide(s);
            case "command" -> command(s);
            case "narrative" -> narrative(s);
            case "time" -> time(s);
            case "win" -> (game, trigger) -> game.win();
            default -> throw new IllegalArgumentException("Unknown action type '" + type + "'");
        };
    }

    // ───────────────────────── feedback ─────────────────────────

    private static RoomAction message(ConfigurationSection s) {
        String text = s.getString("text", "");
        return (game, trigger) -> game.broadcast(Messages.parse(text, Messages.ph("player", name(trigger))));
    }

    private static RoomAction actionBar(ConfigurationSection s) {
        String text = s.getString("text", "");
        return (game, trigger) -> {
            Component c = Messages.parse(text, Messages.ph("player", name(trigger)));
            game.participants().forEach(p -> p.sendActionBar(c));
        };
    }

    private static RoomAction title(ConfigurationSection s) {
        String title = s.getString("title", "");
        String subtitle = s.getString("subtitle", "");
        Title.Times times = Title.Times.times(Duration.ofMillis(s.getLong("fade-in", 10) * 50),
                Duration.ofMillis(s.getLong("stay", 60) * 50), Duration.ofMillis(s.getLong("fade-out", 20) * 50));
        return (game, trigger) -> {
            Title t = Title.title(Messages.parse(title, Messages.ph("player", name(trigger))),
                    Messages.parse(subtitle, Messages.ph("player", name(trigger))), times);
            game.participants().forEach(p -> p.showTitle(t));
        };
    }

    private static RoomAction sound(ConfigurationSection s) {
        Sound sound = Sound.sound(Key.key(s.getString("sound", "block.note_block.pling")), Sound.Source.MASTER,
                (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
        String at = s.getString("location");
        return (game, trigger) -> {
            if (at == null) {
                game.participants().forEach(p -> p.playSound(sound, Sound.Emitter.self()));
            } else {
                Location l = BlockPos.parse(at).center(game.world());
                game.participants().forEach(p -> p.playSound(sound, l.getX(), l.getY(), l.getZ()));
            }
        };
    }

    private static RoomAction particle(ConfigurationSection s) {
        Particle particle = Particle.valueOf(s.getString("particle", "END_ROD").toUpperCase(Locale.ROOT));
        BlockPos pos = BlockPos.parse(s.getString("location", "0,0,0"));
        int count = s.getInt("count", 20);
        double offset = s.getDouble("offset", 0.5);
        double speed = s.getDouble("speed", 0.02);
        Object data = particleData(particle, s.getString("color", "#FFFFFF"), (float) s.getDouble("size", 1.2));
        return (game, trigger) -> spawnParticle(game.world(), particle, pos.center(game.world()), count, offset, speed, data);
    }

    /** Builds the extra data some particles require (dust colour, colour). Null when not needed. */
    public static Object particleData(Particle particle, String hex, float size) {
        Class<?> type = particle.getDataType();
        Color color = Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color, size);
        }
        if (type == Color.class) {
            return color;
        }
        return null;
    }

    public static void spawnParticle(World world, Particle particle, Location at, int count, double offset,
                                     double speed, Object data) {
        Class<?> type = particle.getDataType();
        if (type != Void.class && data == null) {
            return;
        }
        if (data == null) {
            world.spawnParticle(particle, at, count, offset, offset, offset, speed);
        } else {
            world.spawnParticle(particle, at, count, offset, offset, offset, speed, data);
        }
    }

    // ───────────────────────── items & commands ─────────────────────────

    private static RoomAction give(ConfigurationSection s) {
        String item = s.getString("item", "");
        int amount = s.getInt("amount", 1);
        boolean all = "all".equalsIgnoreCase(s.getString("to", "trigger"));
        return (game, trigger) -> {
            ItemStack stack = createItem(game, item, amount);
            if (stack == null) {
                return;
            }
            if (all || trigger == null) {
                if (all) {
                    game.participants().forEach(p -> game.give(p, stack.clone()));
                } else {
                    game.participants().stream().findFirst().ifPresent(p -> game.give(p, stack));
                }
            } else {
                game.give(trigger, stack);
            }
        };
    }

    public static ItemStack createItem(Game game, String id, int amount) {
        ItemSpec spec = game.room().items().get(id);
        if (spec != null) {
            return spec.create(amount);
        }
        Material material = Material.matchMaterial(id);
        return material == null ? null : new ItemStack(material, amount);
    }

    private static RoomAction command(ConfigurationSection s) {
        String command = s.getString("command", "");
        String as = s.getString("as", "console").toLowerCase(Locale.ROOT);
        return (game, trigger) -> {
            if (as.equals("all")) {
                for (Player p : game.participants()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", p.getName()));
                }
            } else if (as.equals("trigger") && trigger != null) {
                trigger.performCommand(command.replace("%player%", trigger.getName()));
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", name(trigger)));
            }
        };
    }

    private static RoomAction time(ConfigurationSection s) {
        long seconds = s.getLong("seconds", 0);
        return (game, trigger) -> game.adjustTime(seconds * 1000L);
    }

    // ───────────────────────── blocks ─────────────────────────

    private static List<BlockPos> positions(ConfigurationSection s) {
        List<BlockPos> list = new ArrayList<>();
        if (s.contains("from") && s.contains("to")) {
            Cuboid c = Cuboid.parse(s.getString("from"), s.getString("to"));
            for (int x = c.minX(); x <= c.maxX(); x++) {
                for (int y = c.minY(); y <= c.maxY(); y++) {
                    for (int z = c.minZ(); z <= c.maxZ(); z++) {
                        list.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (String raw : Cfg.stringList(s, "blocks")) {
            list.add(BlockPos.parse(raw));
        }
        if (s.contains("location")) {
            list.add(BlockPos.parse(s.getString("location")));
        }
        return list;
    }

    private static RoomAction setBlocks(ConfigurationSection s) {
        List<BlockPos> blocks = positions(s);
        BlockData data = Bukkit.createBlockData(s.getString("block", "minecraft:air"));
        boolean physics = s.getBoolean("physics", false);
        return (game, trigger) -> {
            for (BlockPos pos : blocks) {
                pos.block(game.world()).setBlockData(data, physics);
            }
        };
    }

    private static RoomAction open(ConfigurationSection s) {
        List<BlockPos> blocks = positions(s);
        boolean open = s.getBoolean("open", true);
        return (game, trigger) -> {
            for (BlockPos pos : blocks) {
                Block block = pos.block(game.world());
                setOpen(block, open);
                if (block.getBlockData() instanceof Door door) {
                    Block other = door.getHalf() == Bisected.Half.BOTTOM
                            ? block.getRelative(0, 1, 0) : block.getRelative(0, -1, 0);
                    setOpen(other, open);
                }
            }
        };
    }

    private static void setOpen(Block block, boolean open) {
        if (block.getBlockData() instanceof Openable openable) {
            openable.setOpen(open);
            block.setBlockData(openable, false);
        }
    }

    private static RoomAction light(ConfigurationSection s) {
        List<BlockPos> blocks = positions(s);
        boolean lit = s.getBoolean("lit", true);
        return (game, trigger) -> {
            for (BlockPos pos : blocks) {
                Block block = pos.block(game.world());
                if (block.getBlockData() instanceof Lightable lightable) {
                    lightable.setLit(lit);
                    block.setBlockData(lightable, false);
                }
            }
        };
    }

    private static RoomAction redstone(ConfigurationSection s) {
        List<BlockPos> blocks = positions(s);
        boolean power = s.getBoolean("power", true);
        BlockData replaced = Bukkit.createBlockData(s.getString("replace-with", "minecraft:polished_blackstone"));
        return (game, trigger) -> {
            for (BlockPos pos : blocks) {
                Block block = pos.block(game.world());
                block.setBlockData(power ? Material.REDSTONE_BLOCK.createBlockData() : replaced, true);
            }
        };
    }

    /** Animates blocks sliding away with BlockDisplays, then moves the real blocks. */
    private static RoomAction slide(ConfigurationSection s) {
        List<BlockPos> blocks = positions(s);
        BlockPos offset = BlockPos.parse(s.getString("offset", "0,0,1"));
        BlockPos via = s.contains("via") ? BlockPos.parse(s.getString("via")) : null;
        int ticks = Math.max(4, s.getInt("ticks", 30));
        return (game, trigger) -> {
            World world = game.world();
            List<BlockDisplay> displays = new ArrayList<>();
            List<BlockData> datas = new ArrayList<>();
            for (BlockPos pos : blocks) {
                Block block = pos.block(world);
                BlockData data = block.getBlockData();
                datas.add(data);
                BlockDisplay display = world.spawn(new Location(world, pos.x(), pos.y(), pos.z()), BlockDisplay.class, d -> {
                    d.setBlock(data);
                    d.setPersistent(false);
                });
                game.trackTemp(display);
                displays.add(display);
                block.setType(Material.AIR, false);
            }
            int first = via == null ? ticks : ticks / 2;
            Bukkit.getScheduler().runTaskLater(game.plugin(), () -> {
                BlockPos target = via == null ? offset : via;
                displays.forEach(d -> move(d, target, first));
            }, 2L);
            if (via != null) {
                Bukkit.getScheduler().runTaskLater(game.plugin(), () -> displays.forEach(d -> move(d, offset, ticks - first)),
                        2L + first);
            }
            Bukkit.getScheduler().runTaskLater(game.plugin(), () -> {
                for (int i = 0; i < blocks.size(); i++) {
                    BlockPos dest = blocks.get(i).offset(offset.x(), offset.y(), offset.z());
                    dest.block(world).setBlockData(datas.get(i), false);
                }
                displays.forEach(org.bukkit.entity.Entity::remove);
            }, 4L + ticks);
        };
    }

    private static void move(Display display, BlockPos to, int duration) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(new Transformation(new Vector3f(to.x(), to.y(), to.z()), new Quaternionf(),
                new Vector3f(1, 1, 1), new Quaternionf()));
    }

    // ───────────────────────── narrative ─────────────────────────

    private static RoomAction narrative(ConfigurationSection s) {
        String text = s.getString("text", "");
        String at = s.getString("location", "0,0,0");
        int seconds = s.getInt("seconds", 12);
        float scale = (float) s.getDouble("scale", 1.0);
        return (game, trigger) -> {
            Location loc = parseCenter(game.world(), at);
            TextDisplay display = game.world().spawn(loc, TextDisplay.class, d -> {
                d.text(Messages.parse(text));
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setLineWidth(220);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(110, 10, 6, 2));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
            });
            game.trackTemp(display);
            Bukkit.getScheduler().runTaskLater(game.plugin(), display::remove, seconds * 20L);
        };
    }

    /** Parses "x,y,z" where decimals are allowed; integers are centred in the block. */
    public static Location parseCenter(World world, String raw) {
        String[] p = raw.replace(" ", "").split(",");
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            v[i] = Double.parseDouble(p[i]) + (p[i].contains(".") ? 0 : 0.5);
        }
        return new Location(world, v[0], v[1], v[2]);
    }

    private static String name(Player player) {
        return player == null ? "" : player.getName();
    }
}
