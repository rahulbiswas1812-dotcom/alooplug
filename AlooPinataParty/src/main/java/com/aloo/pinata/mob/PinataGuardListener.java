package com.aloo.pinata.mob;

import com.aloo.pinata.AlooPinataParty;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTransformEvent;

/** Stops the event mob from burning in daylight or transforming (zombie to drowned, hoglin to zoglin). */
public final class PinataGuardListener implements Listener {

    private final AlooPinataParty plugin;

    public PinataGuardListener(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (plugin.events().isActivePinata(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (plugin.events().isActivePinata(e.getEntity())) {
            e.setCancelled(true);
        }
    }
}
