package com.aloo.pinata.config;

import com.aloo.pinata.effect.EffectDef;
import com.aloo.pinata.util.Enums;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

/** Immutable, validated snapshot of config.yml. Built once per (re)load; read freely on hot paths. */
public final class Settings {

    // schedule
    public final boolean scheduleEnabled;
    public final long intervalMs;
    public final int announceSeconds;
    public final int[] announceThresholds;
    public final int finalCountdownSeconds;
    public final long eventDurationMs;
    public final ZoneId zone;
    public final boolean catchUpMissed;
    public final int catchUpDelaySeconds;
    public final int manualCountdownSeconds;

    // bossbars
    public final String countdownTitle;
    public final BossBar.Color countdownColor;
    public final BossBar.Overlay countdownOverlay;
    public final String activeTitle;
    public final BossBar.Color activeColor;
    public final BossBar.Overlay activeOverlay;

    // teleport
    public final boolean teleportEnabled;
    public final int teleportDelaySeconds;
    public final boolean teleportCancelOnMove;
    public final boolean teleportDuringCountdown;
    public final int combatBlockSeconds;

    // pinata / damage
    public final boolean pinataGlowing;
    public final boolean pinataDrops;
    public final boolean invulnerableToNonPlayers;
    public final boolean countCreative;
    public final boolean requireAttackerInArena;

    // effects
    public final boolean effectsEnabled;
    public final long buffIntervalMs;
    public final long debuffCooldownMs;
    public final List<EffectDef> buffs;
    public final List<EffectDef> debuffs;

    // protection
    public final boolean protectionEnabled;
    public final boolean protectDuringCountdown;
    public final boolean protectBlockOutgoing;
    public final boolean protectBlockSplash;
    public final boolean protectPersistent;

    // ending
    public final int leaderboardSize;
    public final boolean announceLeaderboardOnTimeout;
    public final boolean showTitle;
    public final List<String> victorySounds;
    public final int lightningEffects;

    // history
    public final boolean historyEnabled;
    public final int historyMaxEntries;

    public Settings(FileConfiguration c, Logger log) {
        scheduleEnabled = c.getBoolean("schedule.enabled", true);

        double hours = c.getDouble("schedule.interval-hours", 3.0);
        if (!Double.isFinite(hours) || hours < 0.02 || hours > 24 * 60) {
            log.warning("schedule.interval-hours is invalid (" + hours + "); using 3.");
            hours = 3.0;
        }
        intervalMs = Math.round(hours * 3_600_000.0);

        int annMin = clamp(c.getInt("schedule.announcement-minutes", 10), 0, 1440, log, "schedule.announcement-minutes");
        announceSeconds = annMin * 60;
        TreeSet<Integer> th = new TreeSet<>(Comparator.reverseOrder());
        if (annMin > 0) {
            th.add(annMin * 60);
        }
        List<?> extra = c.getList("schedule.additional-announcements", List.of());
        if (extra != null) {
            for (Object o : extra) {
                int m = -1;
                if (o instanceof Number n) {
                    m = n.intValue();
                } else if (o != null) {
                    try {
                        m = Integer.parseInt(o.toString().trim());
                    } catch (NumberFormatException ignored) {
                        m = -1;
                    }
                }
                if (m > 0 && m <= annMin) {
                    th.add(m * 60);
                } else {
                    log.warning("schedule.additional-announcements: ignoring '" + o + "' (must be 1.." + annMin + ").");
                }
            }
        }
        announceThresholds = th.stream().mapToInt(Integer::intValue).toArray();

        finalCountdownSeconds = clamp(c.getInt("schedule.final-countdown-seconds", 10), 0, 60, log, "schedule.final-countdown-seconds");
        int durMin = clamp(c.getInt("schedule.event-duration-minutes", 10), 1, 240, log, "schedule.event-duration-minutes");
        eventDurationMs = durMin * 60_000L;

        String tz = c.getString("schedule.timezone", "server");
        ZoneId z = ZoneId.systemDefault();
        if (tz != null && !tz.equalsIgnoreCase("server")) {
            try {
                z = ZoneId.of(tz);
            } catch (DateTimeException ex) {
                log.warning("schedule.timezone '" + tz + "' is invalid; using the server timezone.");
            }
        }
        zone = z;

        catchUpMissed = "CATCH_UP".equalsIgnoreCase(c.getString("schedule.missed-event-policy", "RESCHEDULE"));
        catchUpDelaySeconds = Math.max(annMin * 60 + 60,
                clamp(c.getInt("schedule.catch-up-delay-minutes", 15), 1, 1440, log, "schedule.catch-up-delay-minutes") * 60);
        manualCountdownSeconds = clamp(c.getInt("schedule.manual-countdown-seconds", 60), 0, 3600, log, "schedule.manual-countdown-seconds");

        countdownTitle = c.getString("bossbar.countdown.title", "&6&lALOO PIÑATA PARTY &fSTARTS IN &e%time%");
        countdownColor = Enums.parse(BossBar.Color.class, c.getString("bossbar.countdown.color"), BossBar.Color.YELLOW);
        countdownOverlay = Enums.parse(BossBar.Overlay.class, c.getString("bossbar.countdown.style"), BossBar.Overlay.PROGRESS);
        activeTitle = c.getString("bossbar.active.title", "&6&lALOO PIÑATA PARTY &8| &f%mob% &8| &cHP: &f%hp%&7/&f%max%");
        activeColor = Enums.parse(BossBar.Color.class, c.getString("bossbar.active.color"), BossBar.Color.RED);
        activeOverlay = Enums.parse(BossBar.Overlay.class, c.getString("bossbar.active.style"), BossBar.Overlay.PROGRESS);

        teleportEnabled = c.getBoolean("teleport.enabled", true);
        teleportDelaySeconds = clamp(c.getInt("teleport.delay-seconds", 3), 0, 30, log, "teleport.delay-seconds");
        teleportCancelOnMove = c.getBoolean("teleport.cancel-on-move", true);
        teleportDuringCountdown = c.getBoolean("teleport.allow-during-countdown", true);
        combatBlockSeconds = clamp(c.getInt("teleport.block-in-combat-seconds", 0), 0, 300, log, "teleport.block-in-combat-seconds");

        pinataGlowing = c.getBoolean("pinata.glowing", true);
        pinataDrops = c.getBoolean("pinata.drops", false);
        invulnerableToNonPlayers = c.getBoolean("pinata.invulnerable-to-non-players", true);
        countCreative = c.getBoolean("pinata.count-creative-players", false);
        requireAttackerInArena = c.getBoolean("pinata.require-attacker-in-arena", false);

        effectsEnabled = c.getBoolean("effects.enabled", true);
        buffIntervalMs = clamp(c.getInt("effects.mob-buff-interval-seconds", 30), 5, 3600, log, "effects.mob-buff-interval-seconds") * 1000L;
        debuffCooldownMs = clamp(c.getInt("effects.player-debuff-cooldown-seconds", 5), 0, 300, log, "effects.player-debuff-cooldown-seconds") * 1000L;
        int maxSec = clamp(c.getInt("effects.max-duration-seconds", 30), 1, 600, log, "effects.max-duration-seconds");
        int maxAmp = clamp(c.getInt("effects.max-amplifier", 2), 0, 9, log, "effects.max-amplifier");
        buffs = EffectDef.parse(c.getConfigurationSection("effects.mob-buffs"), maxSec, maxAmp, log, "effects.mob-buffs");
        debuffs = EffectDef.parse(c.getConfigurationSection("effects.player-debuffs"), maxSec, maxAmp, log, "effects.player-debuffs");

        protectionEnabled = c.getBoolean("protection.enabled", true);
        protectDuringCountdown = c.getBoolean("protection.during-countdown", false);
        protectBlockOutgoing = c.getBoolean("protection.block-outgoing", true);
        protectBlockSplash = c.getBoolean("protection.block-splash-potions", true);
        protectPersistent = c.getBoolean("protection.persistent-arena-protection", false);

        leaderboardSize = clamp(c.getInt("ending.leaderboard-size", 5), 1, 10, log, "ending.leaderboard-size");
        announceLeaderboardOnTimeout = c.getBoolean("ending.announce-leaderboard-on-timeout", true);
        showTitle = c.getBoolean("ending.show-title", true);
        victorySounds = List.copyOf(c.getStringList("ending.victory-sounds"));
        lightningEffects = clamp(c.getInt("ending.lightning-effects", 3), 0, 10, log, "ending.lightning-effects");

        historyEnabled = c.getBoolean("history.enabled", false);
        historyMaxEntries = clamp(c.getInt("history.max-entries", 50), 1, 1000, log, "history.max-entries");
    }

    private static int clamp(int v, int lo, int hi, Logger log, String path) {
        if (v < lo || v > hi) {
            int fixed = Math.max(lo, Math.min(hi, v));
            log.warning(path + " = " + v + " is out of range " + lo + ".." + hi + "; using " + fixed + ".");
            return fixed;
        }
        return v;
    }
}
