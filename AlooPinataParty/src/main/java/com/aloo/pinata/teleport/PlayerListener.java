package com.aloo.pinata.teleport;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.util.Attackers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join/quit/death/move housekeeping so no per-player state or bossbar can go stale. */
public final class PlayerListener implements Listener {

    private final AlooPinataParty plugin;

    public PlayerListener(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.bars().onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.bars().onQuit(p);
        plugin.teleports().forget(p.getUniqueId());
        plugin.arena().clearSelection(p.getUniqueId());
        plugin.effects().forget(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.teleports().cancel(e.getEntity().getUniqueId(), "teleport.cancelled");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.teleports().hasPending() || !e.hasChangedBlock()) {
            return;
        }
        plugin.teleports().onMove(e.getPlayer(), e.getTo());
    }

    /** Built-in lightweight combat tag (only active when block-in-combat-seconds > 0). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (plugin.settings().combatBlockSeconds <= 0 || !(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = Attackers.any(e.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            plugin.teleports().markCombat(victim.getUniqueId());
            plugin.teleports().markCombat(attacker.getUniqueId());
        }
    }
}
