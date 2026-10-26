package com.aloo.pinata.bossbar;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.mob.MobProfile;
import com.aloo.pinata.util.Strings;
import com.aloo.pinata.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** The two bossbars. Titles are only rebuilt when their text really changes. */
public final class BossBarManager {

    private final AlooPinataParty plugin;
    private BossBar countdown;
    private BossBar active;
    private String lastCountdownTitle = "";
    private String lastActiveTitle = "";
    private MobProfile activeProfile;

    public BossBarManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    private static boolean eligible(Player p) {
        return p.hasPermission("aloopinata.participate");
    }

    private static float clamp(double v) {
        if (Double.isNaN(v)) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    // ---------------------------------------------------------------- countdown

    public void showCountdown(int remainingSeconds, double progress) {
        Settings s = plugin.settings();
        if (countdown == null) {
            countdown = BossBar.bossBar(Component.empty(), 1f, s.countdownColor, s.countdownOverlay);
            lastCountdownTitle = "";
        }
        updateCountdown(remainingSeconds, progress);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p)) {
                p.showBossBar(countdown);
            }
        }
    }

    public void updateCountdown(int remainingSeconds, double progress) {
        if (countdown == null) {
            return;
        }
        String title = Strings.replace(plugin.settings().countdownTitle, "time", Strings.formatTime(remainingSeconds));
        if (!title.equals(lastCountdownTitle)) {
            lastCountdownTitle = title;
            countdown.name(Text.color(title));
        }
        countdown.progress(clamp(progress));
    }

    public void hideCountdown() {
        if (countdown != null) {
            BossBar bar = countdown;
            countdown = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
    }

    // ---------------------------------------------------------------- active

    public void showActive(MobProfile profile, double hp, double max) {
        activeProfile = profile;
        active = BossBar.bossBar(Component.empty(), 1f, profile.bossBarColor(), profile.bossBarOverlay());
        lastActiveTitle = "";
        updateActive(hp, max);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p)) {
                p.showBossBar(active);
            }
        }
    }

    public void updateActive(double hp, double max) {
        if (active == null || activeProfile == null) {
            return;
        }
        double safeMax = max <= 0 ? 1 : max;
        double clampedHp = Math.max(0, Math.min(hp, safeMax));
        int pct = (int) Math.round(clampedHp / safeMax * 100.0);
        String title = Strings.replace(plugin.settings().activeTitle,
                "mob", activeProfile.bossBarName(),
                "name", activeProfile.displayName(),
                "hp", Integer.toString((int) Math.ceil(clampedHp)),
                "max", Integer.toString((int) Math.ceil(safeMax)),
                "percent", Integer.toString(pct));
        if (!title.equals(lastActiveTitle)) {
            lastActiveTitle = title;
            active.name(Text.color(title));
        }
        active.progress(clamp(clampedHp / safeMax));
    }

    public void hideActive() {
        if (active != null) {
            BossBar bar = active;
            active = null;
            activeProfile = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
    }

    // ---------------------------------------------------------------- players / lifecycle

    public void onJoin(Player p) {
        if (!eligible(p)) {
            return;
        }
        if (countdown != null) {
            p.showBossBar(countdown);
        }
        if (active != null) {
            p.showBossBar(active);
        }
    }

    public void onQuit(Player p) {
        if (countdown != null) {
            p.hideBossBar(countdown);
        }
        if (active != null) {
            p.hideBossBar(active);
        }
    }

    /** Applies changed colour/style after /pinata reload without recreating the bars. */
    public void reload() {
        Settings s = plugin.settings();
        if (countdown != null) {
            countdown.color(s.countdownColor);
            countdown.overlay(s.countdownOverlay);
            lastCountdownTitle = "";
        }
        lastActiveTitle = "";
    }

    public void removeAll() {
        hideCountdown();
        hideActive();
    }
}
