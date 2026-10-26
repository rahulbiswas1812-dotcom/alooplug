package com.aloo.pinata.mob;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;

/** Validated per-mob settings from mobs.yml. */
public record MobProfile(
        String key,
        NamespacedKey typeKey,
        String displayName,
        double health,
        double maxHealth,
        List<String> spawnSounds,
        String bossBarName,
        BossBar.Color bossBarColor,
        BossBar.Overlay bossBarOverlay,
        Map<String, Double> buffChances,
        double rewardMultiplier,
        double playerDamageMultiplier,
        boolean disableSonicBoom,
        List<String> removeGoals) {
}
