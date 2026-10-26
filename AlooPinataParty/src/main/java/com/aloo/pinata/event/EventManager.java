package com.aloo.pinata.event;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Messages;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.damage.DamageTracker.Ranked;
import com.aloo.pinata.mob.MobProfile;
import com.aloo.pinata.scheduler.ScheduleManager;
import com.aloo.pinata.util.Strings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The event state machine: IDLE -> COUNTDOWN -> ACTIVE -> COMPLETING -> IDLE.
 * A single 1-second ticker drives everything; it only compares timestamps and checks ONE entity (never scans worlds).
 */
public final class EventManager {

    public enum StartResult { OK, ALREADY_RUNNING, NOT_READY, NO_MOBS }

    public enum StopResult { NOT_RUNNING, COUNTDOWN_CANCELLED, EVENT_CANCELLED }

    private final AlooPinataParty plugin;
    private final ScheduleManager schedule;
    private EventState state = EventState.IDLE;
    private BukkitTask ticker;

    // countdown
    private long countdownEndMs;
    private long countdownTotalMs = 1000;
    private final Set<Integer> announced = new HashSet<>();
    private MobProfile forcedProfile;
    private boolean manualEvent;
    private long scheduledStartMs;
    private int lastFinalSecond = -1;

    // active
    private String eventId = "-";
    private MobProfile profile;
    private LivingEntity pinata;
    private UUID pinataId;
    private double activeMaxHealth = 1;
    private boolean ticketHeld;
    private int ticketX;
    private int ticketZ;
    private World ticketWorld;
    private long startedAtMs;
    private long endsAtMs;
    private long nextBuffAtMs;
    private boolean barTaskQueued;

    public EventManager(AlooPinataParty plugin) {
        this.plugin = plugin;
        this.schedule = new ScheduleManager(plugin);
    }

    // =============================================================== lifecycle

    public void start() {
        schedule.load();
        schedule.recalc();
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSafe, 20L, 20L);
    }

    public boolean isStarted() {
        return ticker != null;
    }

    /** Re-validates the stored next-event time after /pinata reload or a schedule change. */
    public void reloadSchedule() {
        if (state == EventState.IDLE) {
            schedule.recalc();
        }
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        try {
            if (state == EventState.ACTIVE) {
                end(EndReason.SHUTDOWN);
            } else if (state == EventState.COUNTDOWN) {
                clearCountdown();
                state = EventState.IDLE;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while shutting the event down", t);
        } finally {
            forceCleanup();
            state = EventState.IDLE;
        }
    }

    // =============================================================== accessors

    public EventState state() {
        return state;
    }

    public ScheduleManager schedule() {
        return schedule;
    }

    public MobProfile profile() {
        return profile;
    }

    public String eventId() {
        return eventId;
    }

    public boolean isActivePinata(Entity e) {
        return state == EventState.ACTIVE && pinataId != null && e.getUniqueId().equals(pinataId);
    }

    public boolean protectionActive() {
        Settings s = plugin.settings();
        if (!s.protectionEnabled) {
            return false;
        }
        return s.protectPersistent || state == EventState.ACTIVE
                || (s.protectDuringCountdown && state == EventState.COUNTDOWN);
    }

    public double pinataHealth() {
        LivingEntity e = pinata;
        return e == null ? 0 : e.getHealth();
    }

    public double pinataMaxHealth() {
        return activeMaxHealth;
    }

    public long activeRemainingSeconds() {
        return state == EventState.ACTIVE ? Math.max(0, (endsAtMs - System.currentTimeMillis()) / 1000) : 0;
    }

    public long countdownRemainingSeconds() {
        return state == EventState.COUNTDOWN ? Math.max(0, (countdownEndMs - System.currentTimeMillis()) / 1000) : 0;
    }

    // =============================================================== ticker

    private void tickSafe() {
        try {
            tick();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error in the event ticker - aborting the event to stay safe", t);
            try {
                if (state == EventState.ACTIVE) {
                    end(EndReason.ERROR);
                } else if (state == EventState.COUNTDOWN) {
                    cancelCountdown(true);
                }
            } catch (Throwable t2) {
                plugin.getLogger().log(Level.SEVERE, "Cleanup after error failed - forcing reset", t2);
                forceCleanup();
                state = EventState.IDLE;
            }
        }
    }

    private void tick() {
        switch (state) {
            case IDLE -> tickIdle();
            case COUNTDOWN -> tickCountdown();
            case ACTIVE -> tickActive();
            default -> { }
        }
    }

    private void tickIdle() {
        Settings s = plugin.settings();
        if (!s.scheduleEnabled) {
            return;
        }
        long next = schedule.next();
        if (next <= 0) {
            schedule.recalc();
            return;
        }
        long now = System.currentTimeMillis();
        if (next - now > s.announceSeconds * 1000L) {
            return;
        }
        if (!arenaReady() || plugin.mobs().enabled().isEmpty()) {
            plugin.getLogger().warning("A scheduled Pinata Party could not start: arena/spawn not set or no enabled mobs. "
                    + "Skipping this slot.");
            schedule.pushBack();
            return;
        }
        beginCountdown(Math.max(next, now + 5000L), null, false);
    }

    private boolean arenaReady() {
        return plugin.arena().isReady();
    }

    // =============================================================== countdown

    private void beginCountdown(long endMs, MobProfile forced, boolean manual) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        state = EventState.COUNTDOWN;
        forcedProfile = forced;
        manualEvent = manual;
        scheduledStartMs = manual ? 0 : endMs;
        countdownEndMs = endMs;
        countdownTotalMs = Math.max(1000L, endMs - now);
        int totalSec = (int) (countdownTotalMs / 1000L);
        announced.clear();
        for (int th : s.announceThresholds) {
            if (th > totalSec + 1) {
                announced.add(th);
            }
        }
        lastFinalSecond = -1;
        plugin.bars().showCountdown(totalSec, 1.0);
        tickCountdown();
    }

    private void tickCountdown() {
        Settings s = plugin.settings();
        long remMs = countdownEndMs - System.currentTimeMillis();
        int remSec = (int) Math.ceil(remMs / 1000.0);
        if (remSec <= 0) {
            startEvent();
            return;
        }
        plugin.bars().updateCountdown(remSec, (double) remMs / countdownTotalMs);
        boolean fire = false;
        for (int th : s.announceThresholds) {
            if (remSec <= th && announced.add(th)) {
                fire = true;
            }
        }
        if (fire) {
            Bukkit.getServer().broadcast(joinLine("announce.countdown", "time", Strings.formatTime(remSec)));
        }
        if (s.finalCountdownSeconds > 0 && remSec <= s.finalCountdownSeconds && remSec != lastFinalSecond) {
            lastFinalSecond = remSec;
            finalCountdownTick(remSec);
        }
    }

    private void finalCountdownTick(int seconds) {
        Messages m = plugin.messages();
        Title title = Title.title(
                m.component("announce.final-title", "seconds", Integer.toString(seconds)),
                m.component("announce.final-subtitle"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("aloopinata.participate")) {
                p.showTitle(title);
                p.playSound(p.getLocation(), "block.note_block.hat", 0.8f, 1.0f);
            }
        }
    }

    /** Chat line with a REAL clickable [CLICK TO JOIN] button (runs /pinata join). */
    private Component joinLine(String key, String... kv) {
        Messages m = plugin.messages();
        Component line = m.prefixed(key, kv);
        Settings s = plugin.settings();
        if (!s.teleportEnabled) {
            return line;
        }
        Component button = m.component("announce.join-button")
                .clickEvent(ClickEvent.runCommand("/pinata join"))
                .hoverEvent(HoverEvent.showText(m.component("announce.join-hover",
                        "seconds", Integer.toString(s.teleportDelaySeconds))));
        return line.append(Component.space()).append(button);
    }

    private void clearCountdown() {
        plugin.bars().hideCountdown();
        announced.clear();
        forcedProfile = null;
    }

    private void cancelCountdown(boolean announce) {
        boolean wasManual = manualEvent;
        clearCountdown();
        plugin.teleports().cancelAll("teleport.event-ended");
        state = EventState.IDLE;
        if (!wasManual) {
            schedule.pushBack();
        }
        if (announce) {
            Bukkit.getServer().broadcast(plugin.messages().prefixed("event.countdown-cancelled"));
        }
    }

    // =============================================================== start

    public StartResult startManual(int countdownSeconds, MobProfile forced) {
        if (state == EventState.ACTIVE || state == EventState.COMPLETING) {
            return StartResult.ALREADY_RUNNING;
        }
        if (state == EventState.COUNTDOWN) {
            if (countdownSeconds > 0) {
                return StartResult.ALREADY_RUNNING;
            }
            manualEvent = true;
            forcedProfile = forced != null ? forced : forcedProfile;
            startEvent();
            return state == EventState.ACTIVE ? StartResult.OK : StartResult.NOT_READY;
        }
        if (!arenaReady()) {
            return StartResult.NOT_READY;
        }
        if (forced == null && plugin.mobs().enabled().isEmpty()) {
            return StartResult.NO_MOBS;
        }
        if (countdownSeconds <= 0) {
            manualEvent = true;
            forcedProfile = forced;
            state = EventState.COUNTDOWN;
            startEvent();
            return state == EventState.ACTIVE ? StartResult.OK : StartResult.NOT_READY;
        }
        beginCountdown(System.currentTimeMillis() + countdownSeconds * 1000L, forced, true);
        return StartResult.OK;
    }

    private void startEvent() {
        MobProfile chosen = forcedProfile != null ? forcedProfile : plugin.mobs().pickRandom();
        boolean wasManual = manualEvent;
        long scheduled = scheduledStartMs;
        clearCountdown();
        plugin.teleports().cancelAll(null);
        if (chosen == null || !arenaReady()) {
            plugin.getLogger().warning("Event could not start: no mob or arena not ready.");
            state = EventState.IDLE;
            schedule.afterEvent(wasManual, scheduled);
            return;
        }
        Location loc = plugin.arena().spawnLocation();
        World world = loc.getWorld();
        String id = Long.toString(System.currentTimeMillis() / 1000L, 36)
                + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36), 36);
        try {
            loc.getChunk().load();
            world.addPluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);
            ticketHeld = true;
            ticketWorld = world;
            ticketX = loc.getBlockX() >> 4;
            ticketZ = loc.getBlockZ() >> 4;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not keep the arena chunk loaded", t);
        }
        LivingEntity entity = plugin.mobs().spawn(chosen, loc, id);
        if (entity == null) {
            plugin.getLogger().severe("The Pinata could not be spawned - event cancelled.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("aloopinata.admin")) {
                    plugin.messages().send(p, "event.spawn-failed");
                }
            }
            releaseTicket();
            state = EventState.IDLE;
            schedule.afterEvent(wasManual, scheduled);
            return;
        }
        long now = System.currentTimeMillis();
        eventId = id;
        profile = chosen;
        pinata = entity;
        pinataId = entity.getUniqueId();
        manualEvent = wasManual;
        scheduledStartMs = scheduled;
        activeMaxHealth = entity.getMaxHealth();
        startedAtMs = now;
        endsAtMs = now + plugin.settings().eventDurationMs;
        nextBuffAtMs = now + plugin.settings().buffIntervalMs;
        plugin.tracker().clear();
        plugin.effects().clear();
        state = EventState.ACTIVE;

        plugin.bars().showActive(chosen, entity.getHealth(), activeMaxHealth);
        Bukkit.getServer().broadcast(joinLine("announce.started", "name", chosen.displayName(), "mob", chosen.bossBarName()));
        for (String sound : chosen.spawnSounds()) {
            world.playSound(loc, sound, 2.0f, 1.0f);
        }
        plugin.getLogger().info("Pinata Party " + id + " started with " + chosen.key()
                + (wasManual ? " (manual)" : " (scheduled)"));
    }

    // =============================================================== active

    private void tickActive() {
        LivingEntity e = pinata;
        long now = System.currentTimeMillis();
        if (e == null || (!e.isValid() && !e.isDead())) {
            plugin.getLogger().warning("The Pinata entity disappeared - ending the event.");
            end(EndReason.ERROR);
            return;
        }
        if (e.isDead()) {
            end(EndReason.VICTORY);
            return;
        }
        if (now >= endsAtMs) {
            end(EndReason.TIMEOUT);
            return;
        }
        // keep it inside the arena
        if (plugin.arena().arena() != null && !plugin.arena().contains(e.getLocation())) {
            returnToSpawn(e);
        }
        if (plugin.settings().effectsEnabled && now >= nextBuffAtMs) {
            plugin.effects().rollMobBuff(e, profile);
            nextBuffAtMs = now + plugin.settings().buffIntervalMs;
        }
        plugin.bars().updateActive(e.getHealth(), activeMaxHealth);
    }

    private void returnToSpawn(LivingEntity e) {
        Location spawn = plugin.arena().spawnLocation();
        if (spawn != null) {
            e.teleport(spawn);
            e.setFallDistance(0f);
        }
    }

    /** Called when the Pinata took void damage etc. (the damage itself is already cancelled). */
    public void recoverPinata() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state == EventState.ACTIVE && pinata != null && pinata.isValid()) {
                returnToSpawn(pinata);
            }
        });
    }

    public void onValidHit(Player p) {
        plugin.effects().onPlayerHit(p);
        if (!barTaskQueued) {
            barTaskQueued = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                barTaskQueued = false;
                if (state == EventState.ACTIVE && pinata != null) {
                    plugin.bars().updateActive(pinata.getHealth(), activeMaxHealth);
                }
            });
        }
    }

    public void onPinataDeath() {
        if (state == EventState.ACTIVE) {
            end(EndReason.VICTORY);
        }
    }

    // =============================================================== stop / end

    public StopResult stop() {
        if (state == EventState.COUNTDOWN) {
            cancelCountdown(true);
            return StopResult.COUNTDOWN_CANCELLED;
        }
        if (state == EventState.ACTIVE) {
            end(EndReason.CANCEL);
            return StopResult.EVENT_CANCELLED;
        }
        return StopResult.NOT_RUNNING;
    }

    private void end(EndReason reason) {
        if (state != EventState.ACTIVE) {
            return;
        }
        state = EventState.COMPLETING;
        MobProfile p = profile;
        String id = eventId;
        boolean wasManual = manualEvent;
        long scheduled = scheduledStartMs;
        long now = System.currentTimeMillis();
        long durationSec = Math.max(0, (now - startedAtMs) / 1000L);
        Location where = pinata != null ? pinata.getLocation().clone() : null;
        List<Ranked> ranking = plugin.tracker().ranking();
        double total = plugin.tracker().total();
        try {
            if (reason != EndReason.SHUTDOWN) {
                announce(reason, p, ranking, total, durationSec);
                plugin.rewards().process(id, p.key(), p.rewardMultiplier(), ranking, reason, wasManual);
                if (reason == EndReason.VICTORY) {
                    victoryEffects(where);
                }
            }
            recordHistory(id, p, reason, durationSec, total, ranking);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while finishing the event", t);
        } finally {
            forceCleanup();
            state = EventState.IDLE;
            if (!plugin.isShuttingDown() || reason == EndReason.SHUTDOWN) {
                schedule.afterEvent(wasManual, scheduled);
            }
            plugin.getLogger().info("Pinata Party " + id + " ended: " + reason);
        }
    }

    private void announce(EndReason reason, MobProfile p, List<Ranked> ranking, double total, long durationSec) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        switch (reason) {
            case CANCEL -> Bukkit.getServer().broadcast(m.prefixed("event.cancelled-active"));
            case ERROR -> Bukkit.getServer().broadcast(m.prefixed("event.error-aborted"));
            case TIMEOUT -> Bukkit.getServer().broadcast(m.prefixed("event.timeout", "mob", p.bossBarName(), "name", p.displayName()));
            default -> { }
        }
        boolean board = reason == EndReason.VICTORY || (reason == EndReason.TIMEOUT && s.announceLeaderboardOnTimeout);
        if (board) {
            Bukkit.getServer().broadcast(m.component("leaderboard.header"));
            if (ranking.isEmpty()) {
                Bukkit.getServer().broadcast(m.component("leaderboard.nobody"));
            } else {
                if (reason == EndReason.VICTORY) {
                    Ranked w = ranking.get(0);
                    Bukkit.getServer().broadcast(m.component("leaderboard.winner",
                            "player", Strings.safeName(w.name()), "damage", Strings.formatDamage(w.damage())));
                }
                int n = Math.min(s.leaderboardSize, ranking.size());
                for (int i = 0; i < n; i++) {
                    Ranked r = ranking.get(i);
                    Bukkit.getServer().broadcast(m.component("leaderboard.line",
                            "rank", Integer.toString(r.rank()), "player", Strings.safeName(r.name()),
                            "damage", Strings.formatDamage(r.damage())));
                }
            }
            Bukkit.getServer().broadcast(m.component("leaderboard.total",
                    "total", Strings.formatDamage(total), "duration", Strings.formatTime(durationSec)));
        }
    }

    private void victoryEffects(Location where) {
        Settings s = plugin.settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("aloopinata.participate")) {
                continue;
            }
            for (String sound : s.victorySounds) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }
        if (where != null && where.getWorld() != null && s.lightningEffects > 0) {
            World w = where.getWorld();
            for (int i = 0; i < s.lightningEffects; i++) {
                final int delay = i * 6;
                final double dx = ThreadLocalRandom.current().nextDouble(-4, 4);
                final double dz = ThreadLocalRandom.current().nextDouble(-4, 4);
                Bukkit.getScheduler().runTaskLater(plugin, () -> w.strikeLightningEffect(where.clone().add(dx, 0, dz)), delay);
            }
        }
    }

    private void recordHistory(String id, MobProfile p, EndReason reason, long durationSec, double total, List<Ranked> ranking) {
        if (!plugin.settings().historyEnabled) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("mob", p.key());
        entry.put("result", reason.name());
        entry.put("ended-epoch-ms", System.currentTimeMillis());
        entry.put("duration-seconds", durationSec);
        entry.put("total-damage", Math.round(total * 10) / 10.0);
        entry.put("participants", ranking.size());
        List<String> top = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            Ranked r = ranking.get(i);
            top.add("#" + r.rank() + " " + Strings.safeName(r.name()) + " " + Strings.formatDamage(r.damage()));
        }
        entry.put("top", top);
        plugin.history().record(entry);
    }

    /** Removes the entity, bossbars, tickets, temporary state and pending teleports. Safe to call repeatedly. */
    private void forceCleanup() {
        try {
            plugin.bars().removeAll();
            plugin.teleports().cancelAll(state == EventState.COMPLETING ? "teleport.event-ended" : null);
            LivingEntity e = pinata;
            if (e != null && e.isValid() && !e.isDead()) {
                e.remove();
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Cleanup problem", t);
        }
        pinata = null;
        pinataId = null;
        profile = null;
        forcedProfile = null;
        releaseTicket();
        plugin.tracker().clear();
        plugin.effects().clear();
        announced.clear();
    }

    private void releaseTicket() {
        if (ticketHeld && ticketWorld != null) {
            try {
                ticketWorld.removePluginChunkTicket(ticketX, ticketZ, plugin);
            } catch (Throwable ignored) {
                // world may be gone
            }
        }
        ticketHeld = false;
        ticketWorld = null;
    }
}
