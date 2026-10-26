package com.aloo.pinata.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** One validated, cached potion effect definition. */
public record EffectDef(String name, PotionEffectType type, double chancePercent, int durationTicks, int amplifier) {

    public static List<EffectDef> parse(ConfigurationSection section, int maxSeconds, int maxAmplifier,
                                        Logger log, String label) {
        List<EffectDef> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            String upper = key.toUpperCase(Locale.ROOT);
            NamespacedKey nk = NamespacedKey.fromString("minecraft:" + key.toLowerCase(Locale.ROOT));
            PotionEffectType type = nk == null ? null : Registry.EFFECT.get(nk);
            if (type == null) {
                log.warning("[" + label + "] Unknown potion effect '" + key + "' - skipped.");
                continue;
            }
            double chance = s.getDouble("chance-percent", 0.0);
            if (!Double.isFinite(chance) || chance < 0 || chance > 100) {
                log.warning("[" + label + "] " + key + ".chance-percent must be 0-100; clamped.");
                chance = Double.isFinite(chance) ? Math.max(0, Math.min(100, chance)) : 0;
            }
            int seconds = s.getInt("duration-seconds", 5);
            if (seconds < 1 || seconds > maxSeconds) {
                log.warning("[" + label + "] " + key + ".duration-seconds out of range 1-" + maxSeconds + "; clamped.");
                seconds = Math.max(1, Math.min(maxSeconds, seconds));
            }
            int amp = s.getInt("amplifier", 0);
            if (amp < 0 || amp > maxAmplifier) {
                log.warning("[" + label + "] " + key + ".amplifier out of range 0-" + maxAmplifier + "; clamped.");
                amp = Math.max(0, Math.min(maxAmplifier, amp));
            }
            out.add(new EffectDef(upper, type, chance, seconds * 20, amp));
        }
        return out;
    }
}
