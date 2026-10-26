package com.aloo.pinata.mob;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.util.Enums;
import com.aloo.pinata.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Profiles from mobs.yml, spawning and identification of the event mob. */
public final class MobManager {

    public static final double MAX_HEALTH = 1024.0;

    private final AlooPinataParty plugin;
    private final NamespacedKey tagKey;
    private final Map<String, MobProfile> profiles = new LinkedHashMap<>();

    public MobManager(AlooPinataParty plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "pinata_event");
    }

    // ------------------------------------------------------------------ config

    public void reload(FileConfiguration cfg) {
        profiles.clear();
        disabled.clear();
        ConfigurationSection root = cfg.getConfigurationSection("mobs");
        if (root == null) {
            plugin.getLogger().warning("mobs.yml has no 'mobs' section.");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String upper = key.toUpperCase(Locale.ROOT);
            String typeName = s.getString("entity-type", upper);
            NamespacedKey tk = NamespacedKey.fromString("minecraft:" + typeName.toLowerCase(Locale.ROOT));
            EntityType type = tk == null ? null : Registry.ENTITY_TYPE.get(tk);
            Class<? extends Entity> cls = type == null ? null : type.getEntityClass();
            if (cls == null || !Mob.class.isAssignableFrom(cls)) {
                plugin.getLogger().warning("mobs.yml: '" + key + "' is not a valid mob entity-type; skipped.");
                continue;
            }
            double health = s.getDouble("health", 200.0);
            if (!Double.isFinite(health) || health < 1.0) {
                plugin.getLogger().warning("mobs.yml: " + key + ".health is invalid (" + health + "); using 200.");
                health = 200.0;
            }
            if (health > MAX_HEALTH) {
                plugin.getLogger().warning("mobs.yml: " + key + ".health exceeds " + MAX_HEALTH + "; capped.");
                health = MAX_HEALTH;
            }
            double max = s.getDouble("max-health", health);
            if (!Double.isFinite(max) || max < health) {
                max = health;
            }
            max = Math.min(max, MAX_HEALTH);

            Map<String, Double> chances = new HashMap<>();
            ConfigurationSection bc = s.getConfigurationSection("buff-chances");
            if (bc != null) {
                for (String b : bc.getKeys(false)) {
                    chances.put(b.toUpperCase(Locale.ROOT), Math.max(0, Math.min(100, bc.getDouble(b))));
                }
            }
            List<String> goals = s.contains("remove-goals") ? s.getStringList("remove-goals") : List.of("panic");

            MobProfile profile = new MobProfile(
                    upper,
                    tk,
                    s.getString("name", "&6&lALOO " + upper + " PIÑATA"),
                    health,
                    max,
                    List.copyOf(s.getStringList("spawn-sounds")),
                    s.getString("bossbar.name", upper.replace('_', ' ')),
                    Enums.parse(BossBar.Color.class, s.getString("bossbar.color"), plugin.settings().activeColor),
                    Enums.parse(BossBar.Overlay.class, s.getString("bossbar.style"), plugin.settings().activeOverlay),
                    Map.copyOf(chances),
                    positive(s.getDouble("reward-multiplier", 1.0), 1.0),
                    Math.max(0.0, finite(s.getDouble("player-damage-multiplier", 1.0), 1.0)),
                    s.getBoolean("disable-sonic-boom", false),
                    List.copyOf(goals));
            if (!s.getBoolean("enabled", true)) {
                // keep disabled profiles addressable for sethealth, but out of the random pool
                disabled.put(upper, profile);
                continue;
            }
            profiles.put(upper, profile);
        }
    }

    private final Map<String, MobProfile> disabled = new HashMap<>();

    private static double finite(double v, double fallback) {
        return Double.isFinite(v) ? v : fallback;
    }

    private static double positive(double v, double fallback) {
        return Double.isFinite(v) && v > 0 ? v : fallback;
    }

    public List<MobProfile> enabled() {
        return new ArrayList<>(profiles.values());
    }

    public List<String> allKeys() {
        List<String> keys = new ArrayList<>(profiles.keySet());
        keys.addAll(disabled.keySet());
        return keys;
    }

    /** Enabled or disabled profile by key (case-insensitive); null if unknown. */
    public MobProfile find(String key) {
        if (key == null) {
            return null;
        }
        String k = key.toUpperCase(Locale.ROOT);
        MobProfile p = profiles.get(k);
        return p != null ? p : disabled.get(k);
    }

    public MobProfile pickRandom() {
        if (profiles.isEmpty()) {
            return null;
        }
        List<MobProfile> list = enabled();
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // ------------------------------------------------------------------ identification

    public boolean isPinata(Entity e) {
        return e.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ spawning

    /** Spawns and configures the event mob. Returns null on failure (nothing is left behind). */
    @SuppressWarnings("deprecation")
    public LivingEntity spawn(MobProfile profile, Location loc, String eventId) {
        World world = loc.getWorld();
        EntityType type = profile.typeKey() == null ? null : Registry.ENTITY_TYPE.get(profile.typeKey());
        if (world == null || type == null) {
            return null;
        }
        Entity spawned = null;
        try {
            spawned = world.spawnEntity(loc, type);
            if (!(spawned instanceof LivingEntity le)) {
                spawned.remove();
                return null;
            }
            le.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, eventId);
            le.setPersistent(false);                 // never saved to disk - cannot survive a crash
            le.setRemoveWhenFarAway(false);          // no ordinary despawning
            le.setCanPickupItems(false);
            le.customName(Text.color(profile.displayName()));
            le.setCustomNameVisible(true);
            le.setGlowing(plugin.settings().pinataGlowing);
            if (le instanceof Ageable ageable) {
                ageable.setAdult();
            }
            if (le instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
            double max = Math.min(profile.maxHealth(), MAX_HEALTH);
            le.setMaxHealth(max);
            le.setHealth(Math.max(1.0, Math.min(profile.health(), le.getMaxHealth())));
            if (le instanceof Mob mob) {
                GoalStripper.strip(mob, profile.removeGoals(), plugin.getLogger());
            }
            if (!le.isValid()) {
                // e.g. cancelled by another plugin or peaceful difficulty
                le.remove();
                return null;
            }
            return le;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn the Pinata (" + profile.key() + ")", t);
            if (spawned != null) {
                spawned.remove();
            }
            return null;
        }
    }

    /** Removes leftovers tagged as event mobs (startup only - one pass over loaded entities). */
    public int purgeOrphans(UUIDHolder keep) {
        int removed = 0;
        for (World w : plugin.getServer().getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                if (isPinata(le) && !keep.matches(le)) {
                    le.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Tiny callback so the startup purge can spare a live event mob. */
    public interface UUIDHolder {
        boolean matches(Entity e);
    }
}
