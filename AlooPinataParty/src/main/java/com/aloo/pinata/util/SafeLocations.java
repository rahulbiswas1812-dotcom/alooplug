package com.aloo.pinata.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

/** Landing-spot validation for teleports. */
public final class SafeLocations {

    private static final Set<Material> DANGEROUS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.COBWEB);

    private static final int[] OFFSETS = {0, 1, 2, 3, -1, -2, -3};

    private SafeLocations() {
    }

    /** Returns the exact location if safe, a nearby vertical adjustment if not, or null. */
    public static Location findSafe(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        for (int off : OFFSETS) {
            Location c = target.clone();
            c.setY(target.getY() + off);
            if (isSafe(c)) {
                return c;
            }
        }
        return null;
    }

    public static boolean isSafe(Location l) {
        World w = l.getWorld();
        if (w == null) {
            return false;
        }
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 2) {
            return false;
        }
        if (!isFree(w.getBlockAt(x, y, z)) || !isFree(w.getBlockAt(x, y + 1, z))) {
            return false;
        }
        for (int d = 1; d <= 3; d++) {
            Block b = w.getBlockAt(x, y - d, z);
            Material m = b.getType();
            if (DANGEROUS.contains(m)) {
                return false;
            }
            if (m.isSolid() || m == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFree(Block b) {
        Material m = b.getType();
        if (DANGEROUS.contains(m)) {
            return false;
        }
        return m == Material.WATER || b.isPassable();
    }
}
