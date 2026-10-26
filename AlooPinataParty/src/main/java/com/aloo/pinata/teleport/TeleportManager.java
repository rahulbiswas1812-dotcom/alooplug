package com.aloo.pinata.teleport;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.event.EventState;
import com.aloo.pinata.util.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The delayed /pinata join teleport. One pending teleport per player, cancelled on move/quit/death/event end. */
public final class TeleportManager {

    private static final class Pending {
        final BukkitTask task;
        final String world;
        final int bx;
        final int by;
        final int bz;
        int remaining;

        Pending(BukkitTask task, Location origin, int remaining) {
            this.task = task;
            this.world = origin.getWorld() == null ? "" : origin.getWorld().getName();
            this.bx = origin.getBlockX();
            this.by = origin.getBlockY();
            this.bz = origin.getBlockZ();
            this.remaining = remaining;
        }
    }

    private final AlooPinataParty plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    public TeleportManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public boolean isPending(UUID id) {
        return pending.containsKey(id);
    }

    // ---------------------------------------------------------------- combat tag (optional, built in)

    public void markCombat(UUID id) {
        lastCombat.put(id, System.currentTimeMillis());
    }

    private boolean inCombat(UUID id) {
        int secs = plugin.settings().combatBlockSeconds;
        if (secs <= 0) {
            return false;
        }
        Long t = lastCombat.get(id);
        return t != null && System.currentTimeMillis() - t < secs * 1000L;
    }

    // ---------------------------------------------------------------- request

    public void request(Player p) {
        Settings s = plugin.settings();
        if (!p.hasPermission("aloopinata.participate")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (!s.teleportEnabled) {
            plugin.messages().send(p, "teleport.disabled");
            return;
        }
        EventState st = plugin.events().state();
        boolean allowed = st == EventState.ACTIVE || (st == EventState.COUNTDOWN && s.teleportDuringCountdown);
        if (!allowed || plugin.arena().spawnLocation() == null) {
            plugin.messages().send(p, "teleport.not-available");
            return;
        }
        if (pending.containsKey(p.getUniqueId())) {
            plugin.messages().send(p, "teleport.pending");
            return;
        }
        if (inCombat(p.getUniqueId())) {
            plugin.messages().send(p, "teleport.combat");
            return;
        }
        int delay = s.teleportDelaySeconds;
        if (delay <= 0 || p.hasPermission("aloopinata.teleport.bypass")) {
            teleportNow(p);
            return;
        }
        UUID id = p.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(id), 20L, 20L);
        pending.put(id, new Pending(task, p.getLocation(), delay));
        plugin.messages().send(p, "teleport.start", "seconds", String.valueOf(delay));
    }

    private void tick(UUID id) {
        Pending pd = pending.get(id);
        Player p = Bukkit.getPlayer(id);
        if (pd == null) {
            return;
        }
        if (p == null || !p.isOnline() || p.isDead()) {
            cancel(id, null);
            return;
        }
        // Cheap safety net in addition to the move listener (e.g. teleported away by another plugin).
        if (plugin.settings().teleportCancelOnMove && p.getWorld() != null
                && (!p.getWorld().getName().equals(pd.world))) {
            cancel(id, "teleport.cancelled");
            return;
        }
        pd.remaining--;
        if (pd.remaining <= 0) {
            pending.remove(id);
            pd.task.cancel();
            teleportNow(p);
        } else {
            plugin.messages().send(p, "teleport.tick", "seconds", String.valueOf(pd.remaining));
        }
    }

    /** Called from the move listener; only block-to-block movement matters. */
    public void onMove(Player p, Location to) {
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || !plugin.settings().teleportCancelOnMove) {
            return;
        }
        if (to.getBlockX() != pd.bx || to.getBlockY() != pd.by || to.getBlockZ() != pd.bz) {
            cancel(p.getUniqueId(), "teleport.cancelled-moved");
        }
    }

    public void cancel(UUID id, String messageKey) {
        Pending pd = pending.remove(id);
        if (pd == null) {
            return;
        }
        pd.task.cancel();
        if (messageKey != null) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.messages().send(p, messageKey);
            }
        }
    }

    public void cancelAll(String messageKey) {
        for (UUID id : new ArrayList<>(pending.keySet())) {
            cancel(id, messageKey);
        }
    }

    public void forget(UUID id) {
        cancel(id, null);
        lastCombat.remove(id);
    }

    // ---------------------------------------------------------------- the teleport itself

    private void teleportNow(Player p) {
        EventState st = plugin.events().state();
        if (st != EventState.ACTIVE && st != EventState.COUNTDOWN) {
            plugin.messages().send(p, "teleport.event-ended");
            return;
        }
        Location dest = plugin.arena().spawnLocation();
        if (dest == null) {
            plugin.messages().send(p, "teleport.not-available");
            return;
        }
        Location safe = SafeLocations.findSafe(dest);
        if (safe == null) {
            plugin.messages().send(p, "teleport.unsafe");
            return;
        }
        // The destination keeps the exact configured orientation. Other plugins (combat tags etc.) may still
        // cancel the PlayerTeleportEvent, in which case the future completes with false.
        p.teleportAsync(safe, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                plugin.messages().send(p, Boolean.TRUE.equals(ok) ? "teleport.success" : "teleport.failed");
            });
        });
    }
}
