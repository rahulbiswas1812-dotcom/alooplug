package com.aloo.pinata.damage;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.event.EventManager;
import com.aloo.pinata.mob.MobProfile;
import com.aloo.pinata.util.Attackers;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Pinata-related damage rules. Every handler bails out in a couple of comparisons when no event is active. */
public final class DamageListener implements Listener {

    private final AlooPinataParty plugin;

    public DamageListener(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    /** Only direct player hits / player projectiles may hurt the Pinata. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPinataDamaged(EntityDamageEvent e) {
        EventManager events = plugin.events();
        if (!events.isActivePinata(e.getEntity())) {
            return;
        }
        if (e instanceof EntityDamageByEntityEvent byEntity && Attackers.direct(byEntity.getDamager()) != null) {
            return;
        }
        if (plugin.settings().invulnerableToNonPlayers) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                events.recoverPinata();
            }
        }
    }

    /** Scales or removes the Pinata's damage against players (e.g. Warden). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPinataAttack(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        EventManager events = plugin.events();
        if (!events.isActivePinata(e.getDamager())) {
            return;
        }
        MobProfile profile = events.profile();
        if (profile == null) {
            return;
        }
        if (profile.disableSonicBoom() && e.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) {
            e.setCancelled(true);
            return;
        }
        double m = profile.playerDamageMultiplier();
        if (m != 1.0) {
            e.setDamage(e.getDamage() * m);
        }
    }

    /** Records the damage that actually reached the Pinata, after all other plugins had their say. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPinataHit(EntityDamageByEntityEvent e) {
        EventManager events = plugin.events();
        if (!events.isActivePinata(e.getEntity()) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player p = Attackers.direct(e.getDamager());
        if (p == null || !p.isOnline() || Attackers.isNpc(p)) {
            return;
        }
        Settings s = plugin.settings();
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR || (gm == GameMode.CREATIVE && !s.countCreative)) {
            return;
        }
        if (s.requireAttackerInArena && !plugin.arena().contains(p.getLocation())) {
            return;
        }
        // Real health reduction: final damage (after armor/resistance/absorption) capped at remaining health.
        double dealt = Math.min(e.getFinalDamage(), target.getHealth());
        if (plugin.tracker().record(p.getUniqueId(), p.getName(), dealt, System.currentTimeMillis())) {
            events.onValidHit(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPinataDeath(EntityDeathEvent e) {
        EventManager events = plugin.events();
        if (!events.isActivePinata(e.getEntity())) {
            return;
        }
        if (!plugin.settings().pinataDrops) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        events.onPinataDeath();
    }
}
