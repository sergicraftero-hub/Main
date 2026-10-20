package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.room.ItemSpec;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.logging.Logger;

/**
 * A lock opened by right-clicking a block while holding a tagged key item.
 *
 * <pre>
 * type: key
 * target: "10,64,10"
 * item: llave_oxidada
 * consume: true
 * locked-message: "<gray>La cerradura está oxidada..."
 * </pre>
 */
public final class KeyItemPuzzle extends AbstractPuzzle {

    private final String roomId;
    private final BlockPos target;
    private final String item;
    private final boolean consume;
    private final String lockedMessage;

    public KeyItemPuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "key", s, logger);
        this.roomId = roomId;
        this.target = BlockPos.parse(s.getString("target", "0,0,0"));
        this.item = s.getString("item", "");
        this.consume = s.getBoolean("consume", true);
        this.lockedMessage = s.getString("locked-message", "");
    }

    @Override
    public Set<BlockPos> interactables() {
        return Set.of(target);
    }

    @Override
    public void reset(Game game) {
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        event.setCancelled(true);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if ((roomId + ":" + item).equals(ItemSpec.tagOf(hand))) {
            if (consume) {
                hand.setAmount(hand.getAmount() - 1);
            }
            sound(game, event.getClickedBlock().getLocation(), "block.iron_door.open", 1f, 0.8f);
            solve(game, player);
        } else if (!lockedMessage.isEmpty()) {
            player.sendActionBar(Messages.parse(lockedMessage));
        } else {
            game.plugin().messages().send(player, "puzzle-key-needed");
        }
    }
}
