package com.aloo.pinata.protection;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.arena.Arena;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.util.Attackers;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arena PvP protection: melee, sweep, arrows, tridents, other projectiles, pets, TNT/clouds, splash potions and
 * flame. Only active while an event runs (or always, if persistent-arena-protection is enabled). The Pinata and
 * every other non-player victim are never touched.
 */
public final class ProtectionListener implements Listener {

    private final AlooPinataParty plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public ProtectionListener(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    private boolean protects(Player victim, Player attacker) {
        Arena arena = plugin.arena().arena();
        if (arena == null || attacker.equals(victim) || Attackers.isNpc(victim)) {
            return false;
        }
        Settings s = plugin.settings();
        if (arena.contains(victim.getLocation())) {
            return true;
        }
        return s.protectBlockOutgoing && arena.contains(attacker.getLocation());
    }

    private boolean active() {
        return plugin.events().protectionActive();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !active()) {
            return;
        }
        Player attacker = Attackers.any(e.getDamager());
        if (attacker == null || !protects(victim, attacker)) {
            return;
        }
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(attacker.getUniqueId());
        if (last == null || now - last > 2000) {
            lastNotice.put(attacker.getUniqueId(), now);
            attacker.sendActionBar(plugin.messages().component("protection.blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !active()) {
            return;
        }
        Player attacker = Attackers.any(e.getCombuster());
        if (attacker != null && protects(victim, attacker)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!plugin.settings().protectBlockSplash || !active()) {
            return;
        }
        ThrownPotion potion = e.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) {
            return;
        }
        for (LivingEntity le : new ArrayList<>(e.getAffectedEntities())) {
            if (le instanceof Player victim && protects(victim, thrower)) {
                e.setIntensity(le, 0.0);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastNotice.remove(e.getPlayer().getUniqueId());
    }
}
