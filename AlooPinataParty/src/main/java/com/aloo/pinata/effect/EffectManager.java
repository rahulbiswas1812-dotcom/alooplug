package com.aloo.pinata.effect;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.mob.MobProfile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Random Pinata buffs and player debuffs. Effect definitions are validated and cached in Settings. */
public final class EffectManager {

    private final AlooPinataParty plugin;
    private final Map<UUID, Long> lastDebuff = new HashMap<>();

    public EffectManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    /** One roll: at most one buff is applied, never stacking on a buff that is still running. */
    public void rollMobBuff(LivingEntity mob, MobProfile profile) {
        Settings s = plugin.settings();
        if (!s.effectsEnabled || s.buffs.isEmpty()) {
            return;
        }
        List<EffectDef> pool = new ArrayList<>(s.buffs);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        for (EffectDef def : pool) {
            if (mob.hasPotionEffect(def.type())) {
                continue;
            }
            double chance = profile.buffChances().getOrDefault(def.name(), def.chancePercent());
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < chance) {
                mob.addPotionEffect(new PotionEffect(def.type(), def.durationTicks(), def.amplifier(), false, true));
                return;
            }
        }
    }

    /** Called only after a player dealt valid damage to the Pinata. Respects the per-player cooldown. */
    public void onPlayerHit(Player p) {
        Settings s = plugin.settings();
        if (!s.effectsEnabled || s.debuffs.isEmpty() || !p.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastDebuff.get(p.getUniqueId());
        if (last != null && now - last < s.debuffCooldownMs) {
            return;
        }
        List<EffectDef> pool = new ArrayList<>(s.debuffs);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        for (EffectDef def : pool) {
            if (p.hasPotionEffect(def.type())) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < def.chancePercent()) {
                p.addPotionEffect(new PotionEffect(def.type(), def.durationTicks(), def.amplifier(), false, true));
                lastDebuff.put(p.getUniqueId(), now);
                return;
            }
        }
    }

    public void forget(UUID id) {
        lastDebuff.remove(id);
    }

    public void clear() {
        lastDebuff.clear();
    }
}
