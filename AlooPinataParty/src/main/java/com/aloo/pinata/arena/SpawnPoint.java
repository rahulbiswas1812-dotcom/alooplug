package com.aloo.pinata.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

    /** Null when the world is not loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
