package com.aloo.pinata.arena;

import org.bukkit.Location;
import org.bukkit.World;

/** Axis-aligned cuboid in one world. Containment checks are a handful of comparisons. */
public record Arena(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Arena of(BlockPos a, BlockPos b) {
        return new Arena(a.world(),
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public boolean containsPoint(String worldName, double x, double y, double z) {
        return world.equals(worldName)
                && x >= minX && x < maxX + 1.0
                && y >= minY && y < maxY + 1.0
                && z >= minZ && z < maxZ + 1.0;
    }

    public boolean contains(Location l) {
        if (l == null) {
            return false;
        }
        World w = l.getWorld();
        return w != null && containsPoint(w.getName(), l.getX(), l.getY(), l.getZ());
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
