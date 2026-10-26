package com.aloo.pinata.reward;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.damage.DamageTracker.Ranked;
import com.aloo.pinata.event.EndReason;
import com.aloo.pinata.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Builds reward commands and runs them from the console, a few per tick.
 * Idempotent per event id. Normal players can never inject commands: only commands written in rewards.yml are run
 * and the only substituted values are validated player names, numbers and the mob key.
 */
public final class RewardManager {

    private final AlooPinataParty plugin;
    private final Set<String> processed = new LinkedHashSet<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private BukkitTask drainTask;

    private boolean requireVictory = true;
    private boolean giveOnCancel;
    private boolean giveOnManual = true;
    private double minimumDamage = 1.0;
    private boolean offlinePlayers;
    private int perTick = 10;
    private boolean participationEnabled;
    private List<String> participation = List.of();
    private boolean topEnabled;
    private List<String> topDamage = List.of();
    private boolean leaderboardEnabled;
    private final Map<Integer, List<String>> leaderboard = new HashMap<>();

    public RewardManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration c) {
        requireVictory = c.getBoolean("rewards.require-victory", true);
        giveOnCancel = c.getBoolean("rewards.give-on-cancel", false);
        giveOnManual = c.getBoolean("rewards.give-on-manual-events", true);
        minimumDamage = Math.max(0.0, c.getDouble("rewards.minimum-damage", 1.0));
        offlinePlayers = c.getBoolean("rewards.give-to-offline-players", false);
        perTick = Math.max(1, Math.min(200, c.getInt("rewards.commands-per-tick", 10)));
        participationEnabled = c.getBoolean("rewards.participation.enabled", true);
        participation = List.copyOf(c.getStringList("rewards.participation.commands"));
        topEnabled = c.getBoolean("rewards.top-damage.enabled", true);
        topDamage = List.copyOf(c.getStringList("rewards.top-damage.commands"));
        leaderboardEnabled = c.getBoolean("rewards.leaderboard.enabled", true);
        leaderboard.clear();
        ConfigurationSection sec = c.getConfigurationSection("rewards.leaderboard.rewards");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    leaderboard.put(Integer.parseInt(k.trim()), List.copyOf(sec.getStringList(k + ".commands")));
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("rewards.yml: leaderboard rank '" + k + "' is not a number; ignored.");
                }
            }
        }
    }

    public boolean canReward(EndReason reason, boolean manualEvent) {
        if (manualEvent && !giveOnManual) {
            return false;
        }
        return switch (reason) {
            case VICTORY -> true;
            case TIMEOUT -> !requireVictory;
            case CANCEL -> giveOnCancel;
            default -> false;
        };
    }

    /** Queues rewards for a finished event exactly once. Returns the number of queued commands. */
    public int process(String eventId, String mobKey, double multiplier, List<Ranked> ranking,
                       EndReason reason, boolean manualEvent) {
        if (!canReward(reason, manualEvent)) {
            return 0;
        }
        if (!processed.add(eventId)) {
            plugin.getLogger().warning("Rewards for event " + eventId + " were already processed - ignoring duplicate call.");
            return 0;
        }
        while (processed.size() > 50) {
            Iterator<String> it = processed.iterator();
            it.next();
            it.remove();
        }
        int count = 0;
        for (Ranked r : ranking) {
            if (r.damage() < minimumDamage) {
                continue;
            }
            if (!offlinePlayers && Bukkit.getPlayer(r.id()) == null) {
                continue;
            }
            for (String cmd : commandsFor(r.rank(), r.name(), r.id().toString(), r.damage(), mobKey, eventId, multiplier)) {
                queue.add(cmd);
                count++;
            }
        }
        startDrain();
        plugin.getLogger().info("Event " + eventId + ": queued " + count + " reward command(s).");
        return count;
    }

    /** Commands a player of the given rank would receive (used by rewards and /pinata testrewards). */
    public List<String> commandsFor(int rank, String name, String uuid, double damage, String mobKey,
                                    String eventId, double multiplier) {
        List<String> out = new ArrayList<>();
        if (!Strings.isSafeName(name)) {
            plugin.getLogger().warning("Skipping rewards for player with unsafe name: " + name);
            return out;
        }
        List<String> templates = new ArrayList<>();
        if (participationEnabled) {
            templates.addAll(participation);
        }
        if (rank == 1 && topEnabled) {
            templates.addAll(topDamage);
        }
        if (leaderboardEnabled && leaderboard.containsKey(rank)) {
            templates.addAll(leaderboard.get(rank));
        }
        for (String t : templates) {
            if (t == null || t.isBlank()) {
                continue;
            }
            out.add(RewardFormatter.expand(t, name, uuid, damage, rank, mobKey, eventId, multiplier));
        }
        return out;
    }

    /** Queues already-built commands (used by /pinata testrewards execute). */
    public void enqueue(List<String> commands) {
        queue.addAll(commands);
        startDrain();
    }

    private void startDrain() {
        if (drainTask != null || queue.isEmpty()) {
            return;
        }
        drainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < perTick && !queue.isEmpty(); i++) {
                dispatch(queue.poll());
            }
            if (queue.isEmpty() && drainTask != null) {
                drainTask.cancel();
                drainTask = null;
            }
        }, 1L, 1L);
    }

    private void dispatch(String command) {
        try {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!ok) {
                plugin.getLogger().warning("Reward command was not accepted by the server: /" + command);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Reward command threw an error: /" + command, t);
        }
    }

    /** Runs everything still queued (plugin shutdown). */
    public void flushNow() {
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        while (!queue.isEmpty()) {
            dispatch(queue.poll());
        }
    }
}
