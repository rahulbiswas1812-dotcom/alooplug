package com.aloo.pinata.util;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;

/** Resolves which player is responsible for a damage source. */
public final class Attackers {

    private Attackers() {
    }

    /** Strict: the player themself, or a projectile they shot. Used for damage credit on the Piñata. */
    public static Player direct(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Broad: also clouds, primed TNT and tamed pets. Used by PvP protection. */
    public static Player any(Entity damager) {
        Player p = direct(damager);
        if (p != null) {
            return p;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player s) {
            return s;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player s) {
            return s;
        }
        if (damager instanceof Tameable pet && pet.isTamed() && pet.getOwner() instanceof Player s) {
            return s;
        }
        return null;
    }

    public static boolean isNpc(Entity e) {
        return e.hasMetadata("NPC");
    }
}
