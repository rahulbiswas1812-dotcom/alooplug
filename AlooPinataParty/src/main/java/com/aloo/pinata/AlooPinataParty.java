package com.aloo.pinata;

import com.aloo.pinata.arena.ArenaManager;
import com.aloo.pinata.arena.WandListener;
import com.aloo.pinata.bossbar.BossBarManager;
import com.aloo.pinata.command.PinataCommand;
import com.aloo.pinata.config.ConfigManager;
import com.aloo.pinata.config.Messages;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.damage.DamageListener;
import com.aloo.pinata.damage.DamageTracker;
import com.aloo.pinata.effect.EffectManager;
import com.aloo.pinata.event.EventManager;
import com.aloo.pinata.event.HistoryManager;
import com.aloo.pinata.mob.MobManager;
import com.aloo.pinata.mob.PinataGuardListener;
import com.aloo.pinata.protection.ProtectionListener;
import com.aloo.pinata.reward.RewardManager;
import com.aloo.pinata.teleport.PlayerListener;
import com.aloo.pinata.teleport.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/** ALOO Piñata Party 2.0 - automatic server-wide Piñata boss event. */
public final class AlooPinataParty extends JavaPlugin {

    private ConfigManager configs;
    private Messages messages;
    private ArenaManager arena;
    private MobManager mobs;
    private BossBarManager bars;
    private EffectManager effects;
    private DamageTracker tracker;
    private RewardManager rewards;
    private TeleportManager teleports;
    private HistoryManager history;
    private EventManager events;
    private volatile boolean shuttingDown;

    @Override
    public void onEnable() {
        try {
            configs = new ConfigManager(this);
            messages = new Messages();
            arena = new ArenaManager(this);
            mobs = new MobManager(this);
            bars = new BossBarManager(this);
            effects = new EffectManager(this);
            tracker = new DamageTracker();
            rewards = new RewardManager(this);
            teleports = new TeleportManager(this);
            history = new HistoryManager(this);
            events = new EventManager(this);

            applyConfig();

            // Listeners are registered exactly once; /pinata reload never touches them.
            getServer().getPluginManager().registerEvents(new WandListener(this), this);
            getServer().getPluginManager().registerEvents(new DamageListener(this), this);
            getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new PinataGuardListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

            PluginCommand cmd = getCommand("pinata");
            if (cmd == null) {
                throw new IllegalStateException("Command 'pinata' missing from plugin.yml");
            }
            PinataCommand executor = new PinataCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);

            events.start();

            // One startup pass: remove event mobs left over from a crash (they are non-persistent anyway).
            Bukkit.getScheduler().runTask(this, () -> {
                int n = mobs.purgeOrphans(e -> false);
                if (n > 0) {
                    getLogger().info("Removed " + n + " leftover Pinata mob(s).");
                }
            });
            getLogger().info("ALOO Piñata Party " + getPluginMeta().getVersion() + " enabled.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to enable ALOO Piñata Party - disabling.", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** (Re)loads every file and pushes the values to the managers. Safe to call at any time. */
    private void applyConfig() {
        configs.load();
        messages.reload(configs.messages());
        arena.load(configs.arena());
        mobs.reload(configs.mobs());
        rewards.reload(configs.rewards());
        bars.reload();
        if (events.isStarted()) {
            events.reloadSchedule();
        }
    }

    public boolean reloadAll() {
        try {
            applyConfig();
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Reload failed", t);
            return false;
        }
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        try {
            if (events != null) {
                events.shutdown();
            }
            if (teleports != null) {
                teleports.cancelAll(null);
            }
            if (bars != null) {
                bars.removeAll();
            }
            if (rewards != null) {
                rewards.flushNow();
            }
            if (effects != null) {
                effects.clear();
            }
            if (tracker != null) {
                tracker.clear();
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error during shutdown", t);
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public ConfigManager configs() {
        return configs;
    }

    public Settings settings() {
        return configs.settings();
    }

    public Messages messages() {
        return messages;
    }

    public ArenaManager arena() {
        return arena;
    }

    public MobManager mobs() {
        return mobs;
    }

    public BossBarManager bars() {
        return bars;
    }

    public EffectManager effects() {
        return effects;
    }

    public DamageTracker tracker() {
        return tracker;
    }

    public RewardManager rewards() {
        return rewards;
    }

    public TeleportManager teleports() {
        return teleports;
    }

    public HistoryManager history() {
        return history;
    }

    public EventManager events() {
        return events;
    }
}
