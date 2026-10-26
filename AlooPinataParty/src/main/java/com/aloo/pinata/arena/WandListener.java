package com.aloo.pinata.arena;

import com.aloo.pinata.AlooPinataParty;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class WandListener implements Listener {

    private final AlooPinataParty plugin;

    public WandListener(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ArenaManager arena = plugin.arena();
        if (!arena.isWand(e.getItem())) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("aloopinata.wand")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        BlockPos pos;
        String key;
        if (a == Action.RIGHT_CLICK_BLOCK) {
            pos = arena.setPos1(p, b);
            key = "wand.pos1";
        } else {
            pos = arena.setPos2(p, b);
            key = "wand.pos2";
        }
        plugin.messages().send(p, key, "world", pos.world(), "x", String.valueOf(pos.x()),
                "y", String.valueOf(pos.y()), "z", String.valueOf(pos.z()));
        long vol = arena.selectionVolume(p);
        if (vol > 0) {
            plugin.messages().send(p, "wand.both-set", "volume", String.valueOf(vol));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.arena().isWand(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
        }
    }
}
