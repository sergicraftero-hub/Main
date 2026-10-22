package com.sergicraftero.escapes.room;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.profile.PlayerProfile;
import org.bukkit.entity.Player;

/** Who may play which room: level, previous rooms, premium ownership or permission. */
public final class AccessService {

    private final EscapePlugin plugin;

    public AccessService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    /** Message key explaining why the player can't play, or null when they can. */
    public String denyReason(Player p, RoomDef room) {
        if (!room.enabled) {
            return "room.disabled";
        }
        if (p.hasPermission("escaperooms.bypass.access")) {
            return null;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        if (plugin.progress().level(prof.xp) < room.unlockLevel) {
            return "room.locked-level";
        }
        for (String req : room.unlockRooms) {
            if (!prof.completed(req)) {
                return "room.locked-rooms";
            }
        }
        if (room.isPaid() && !owns(p, room)) {
            return "room.locked-premium";
        }
        return null;
    }

    public boolean owns(Player p, RoomDef room) {
        if (!room.isPaid()) {
            return true;
        }
        if (plugin.profiles().get(p).ownedRooms.contains(room.id)) {
            return true;
        }
        if (p.hasPermission("escaperooms.room." + room.id) || p.hasPermission("escaperooms.rooms.all")) {
            return true;
        }
        return !room.accessPermission.isEmpty() && p.hasPermission(room.accessPermission);
    }

    public boolean unlocked(Player p, RoomDef room) {
        return denyReason(p, room) == null;
    }
}
