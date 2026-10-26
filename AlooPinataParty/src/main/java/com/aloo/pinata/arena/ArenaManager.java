package com.aloo.pinata.arena;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.util.Strings;
import com.aloo.pinata.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Arena region, spawn point, wand and per-player selections. Persisted in arena.yml. */
public final class ArenaManager {

    public enum SaveResult { OK, NEED_BOTH, DIFFERENT_WORLDS, WORLD_UNLOADED, NEEDS_CONFIRM, OUTSIDE_ARENA }

    private final AlooPinataParty plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, BlockPos> pos1 = new HashMap<>();
    private final Map<UUID, BlockPos> pos2 = new HashMap<>();
    private Arena arena;
    private SpawnPoint spawn;

    public ArenaManager(AlooPinataParty plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "arena_wand");
    }

    // ---------------------------------------------------------------- persistence

    public void load(FileConfiguration f) {
        arena = null;
        spawn = null;
        String aw = f.getString("arena.world", "");
        if (aw != null && !aw.isBlank()) {
            arena = Arena.of(
                    new BlockPos(aw, f.getInt("arena.min.x"), f.getInt("arena.min.y"), f.getInt("arena.min.z")),
                    new BlockPos(aw, f.getInt("arena.max.x"), f.getInt("arena.max.y"), f.getInt("arena.max.z")));
            if (Bukkit.getWorld(aw) == null) {
                plugin.getLogger().warning("Arena world '" + aw + "' is not loaded right now.");
            }
        }
        String sw = f.getString("spawn.world", "");
        if (sw != null && !sw.isBlank()) {
            spawn = new SpawnPoint(sw, f.getDouble("spawn.x"), f.getDouble("spawn.y"), f.getDouble("spawn.z"),
                    (float) f.getDouble("spawn.yaw"), (float) f.getDouble("spawn.pitch"));
            if (Bukkit.getWorld(sw) == null) {
                plugin.getLogger().warning("Spawn world '" + sw + "' is not loaded right now.");
            }
        }
    }

    private void persist() {
        FileConfiguration f = plugin.configs().arena();
        if (arena != null) {
            f.set("arena.world", arena.world());
            f.set("arena.min.x", arena.minX());
            f.set("arena.min.y", arena.minY());
            f.set("arena.min.z", arena.minZ());
            f.set("arena.max.x", arena.maxX());
            f.set("arena.max.y", arena.maxY());
            f.set("arena.max.z", arena.maxZ());
        }
        if (spawn != null) {
            f.set("spawn.world", spawn.world());
            f.set("spawn.x", spawn.x());
            f.set("spawn.y", spawn.y());
            f.set("spawn.z", spawn.z());
            f.set("spawn.yaw", (double) spawn.yaw());
            f.set("spawn.pitch", (double) spawn.pitch());
        }
        plugin.configs().save("arena.yml");
    }

    // ---------------------------------------------------------------- queries

    public Arena arena() {
        return arena;
    }

    public SpawnPoint spawn() {
        return spawn;
    }

    /** Spawn as a Location, or null when unset or its world is unloaded. */
    public Location spawnLocation() {
        return spawn == null ? null : spawn.toLocation();
    }

    public boolean isReady() {
        Location l = spawnLocation();
        return arena != null && l != null && Bukkit.getWorld(arena.world()) != null;
    }

    public boolean contains(Location l) {
        return arena != null && arena.contains(l);
    }

    // ---------------------------------------------------------------- wand

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().component("wand.name"));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().rawList("wand.lore")) {
            lore.add(Text.color(line));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Recognised only by hidden persistent data, never by name. */
    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- selection

    public BlockPos setPos1(Player p, Block b) {
        BlockPos pos = new BlockPos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        pos1.put(p.getUniqueId(), pos);
        return pos;
    }

    public BlockPos setPos2(Player p, Block b) {
        BlockPos pos = new BlockPos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        pos2.put(p.getUniqueId(), pos);
        return pos;
    }

    /** Volume of the current selection or -1 if incomplete / cross-world. */
    public long selectionVolume(Player p) {
        BlockPos a = pos1.get(p.getUniqueId());
        BlockPos b = pos2.get(p.getUniqueId());
        if (a == null || b == null || !a.world().equals(b.world())) {
            return -1;
        }
        return Arena.of(a, b).volume();
    }

    public void clearSelection(UUID id) {
        pos1.remove(id);
        pos2.remove(id);
    }

    public SaveResult saveArena(Player p, boolean confirm) {
        BlockPos a = pos1.get(p.getUniqueId());
        BlockPos b = pos2.get(p.getUniqueId());
        if (a == null || b == null) {
            return SaveResult.NEED_BOTH;
        }
        if (!a.world().equals(b.world())) {
            return SaveResult.DIFFERENT_WORLDS;
        }
        if (Bukkit.getWorld(a.world()) == null) {
            return SaveResult.WORLD_UNLOADED;
        }
        if (arena != null && !confirm) {
            return SaveResult.NEEDS_CONFIRM;
        }
        arena = Arena.of(a, b);
        persist();
        return SaveResult.OK;
    }

    public SaveResult saveSpawn(Player p, boolean confirm) {
        Location l = p.getLocation();
        if (l.getWorld() == null || Bukkit.getWorld(l.getWorld().getName()) == null) {
            return SaveResult.WORLD_UNLOADED;
        }
        if (arena != null && !arena.contains(l)) {
            return SaveResult.OUTSIDE_ARENA;
        }
        if (spawn != null && !confirm) {
            return SaveResult.NEEDS_CONFIRM;
        }
        spawn = new SpawnPoint(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        persist();
        return SaveResult.OK;
    }

    public boolean spawnInsideArena() {
        Location l = spawnLocation();
        return arena == null || l == null || arena.contains(l);
    }

    // ---------------------------------------------------------------- display

    public String describeSpawn() {
        if (spawn == null) {
            return "not set";
        }
        return spawn.world() + " " + Strings.formatNumber(Math.round(spawn.x() * 10) / 10.0) + ", "
                + Strings.formatNumber(Math.round(spawn.y() * 10) / 10.0) + ", "
                + Strings.formatNumber(Math.round(spawn.z() * 10) / 10.0);
    }

    public String describeArenaShort() {
        return arena == null ? "not set" : arena.world();
    }
}
