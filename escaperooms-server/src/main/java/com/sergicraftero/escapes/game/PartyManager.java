package com.sergicraftero.escapes.game;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Groups of friends (up to 10) who play together. The leader picks the room. */
public final class PartyManager {

    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return members;
        }

        public List<Player> online() {
            List<Player> out = new ArrayList<>();
            for (UUID u : members) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }

        public boolean isLeader(Player p) {
            return leader.equals(p.getUniqueId());
        }
    }

    private final EscapePlugin plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>(); // invited → leader
    private final Map<UUID, Long> inviteTime = new HashMap<>();

    public PartyManager(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public Party of(Player p) {
        return byMember.get(p.getUniqueId());
    }

    public int max() {
        return Math.min(10, Math.max(2, plugin.getConfig().getInt("party.max-size", 10)));
    }

    public void invite(Player leader, Player target) {
        if (leader.equals(target)) {
            Msg.send(leader, "party.invite-self");
            return;
        }
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) {
            party = new Party(leader.getUniqueId());
            byMember.put(leader.getUniqueId(), party);
        }
        if (!party.isLeader(leader)) {
            Msg.send(leader, "party.not-leader");
            return;
        }
        if (party.members().size() >= max()) {
            Msg.send(leader, "party.full", "max", max());
            return;
        }
        if (byMember.containsKey(target.getUniqueId())) {
            Msg.send(leader, "party.target-in-party", "player", target.getName());
            return;
        }
        invites.put(target.getUniqueId(), leader.getUniqueId());
        inviteTime.put(target.getUniqueId(), System.currentTimeMillis());
        Msg.send(leader, "party.invited", "player", target.getName());
        target.sendMessage(Msg.get("party.invite-received", "player", leader.getName())
                .clickEvent(ClickEvent.runCommand("/grupo aceptar")));
    }

    public void accept(Player p) {
        UUID leaderId = invites.remove(p.getUniqueId());
        Long when = inviteTime.remove(p.getUniqueId());
        if (leaderId == null || when == null || System.currentTimeMillis() - when > 120_000) {
            Msg.send(p, "party.no-invite");
            return;
        }
        Party party = byMember.get(leaderId);
        if (party == null || !party.leader().equals(leaderId)) {
            Msg.send(p, "party.no-invite");
            return;
        }
        if (party.members().size() >= max()) {
            Msg.send(p, "party.full", "max", max());
            return;
        }
        leave(p, false);
        party.members().add(p.getUniqueId());
        byMember.put(p.getUniqueId(), party);
        for (Player m : party.online()) {
            Msg.send(m, "party.joined", "player", p.getName(), "size", party.members().size(), "max", max());
        }
    }

    public void leave(Player p, boolean announce) {
        Party party = byMember.remove(p.getUniqueId());
        if (party == null) {
            return;
        }
        party.members().remove(p.getUniqueId());
        if (announce) {
            Msg.send(p, "party.you-left");
            for (Player m : party.online()) {
                Msg.send(m, "party.left", "player", p.getName());
            }
        }
        if (party.members().isEmpty()) {
            return;
        }
        if (party.leader().equals(p.getUniqueId())) {
            party.leader = party.members().iterator().next();
            Player newLeader = Bukkit.getPlayer(party.leader);
            for (Player m : party.online()) {
                Msg.send(m, "party.new-leader", "player", newLeader == null ? "?" : newLeader.getName());
            }
        }
        if (party.members().size() == 1) {
            UUID last = party.members().iterator().next();
            byMember.remove(last);
            Player lp = Bukkit.getPlayer(last);
            if (lp != null) {
                Msg.send(lp, "party.disbanded");
            }
        }
    }

    public void kick(Player leader, String name) {
        Party party = of(leader);
        if (party == null || !party.isLeader(leader)) {
            Msg.send(leader, "party.not-leader");
            return;
        }
        for (UUID u : new ArrayList<>(party.members())) {
            Player m = Bukkit.getPlayer(u);
            String mName = m != null ? m.getName() : Bukkit.getOfflinePlayer(u).getName();
            if (mName != null && mName.equalsIgnoreCase(name) && !u.equals(leader.getUniqueId())) {
                if (m != null) {
                    leave(m, false);
                    Msg.send(m, "party.kicked");
                } else {
                    party.members().remove(u);
                    byMember.remove(u);
                }
                for (Player o : party.online()) {
                    Msg.send(o, "party.left", "player", mName);
                }
                return;
            }
        }
        Msg.send(leader, "party.not-member", "player", name);
    }

    public void quit(Player p) {
        // Offline members stay in the party for a while; they are removed if they don't come back.
        invites.remove(p.getUniqueId());
    }
}
