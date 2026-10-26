package com.aloo.pinata.mob;

import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Removes named vanilla AI goals (e.g. "panic") through Paper's public MobGoals API.
 * Reflection is used only for the generic goal/key accessors so the class stays binary compatible
 * across Paper versions; any failure is logged and ignored (the Pinata simply keeps vanilla behaviour).
 */
final class GoalStripper {

    private GoalStripper() {
    }

    static void strip(Mob mob, Collection<String> names, Logger log) {
        if (names.isEmpty()) {
            return;
        }
        try {
            MobGoals goals = Bukkit.getMobGoals();
            Class<?> goalCls = Class.forName("com.destroystokyo.paper.entity.ai.Goal");
            Class<?> keyCls = Class.forName("com.destroystokyo.paper.entity.ai.GoalKey");
            Method getAll = MobGoals.class.getMethod("getAllGoals", Mob.class);
            Method remove = MobGoals.class.getMethod("removeGoal", Mob.class, goalCls);
            Method getKey = goalCls.getMethod("getKey");
            Method getNamespaced = keyCls.getMethod("getNamespacedKey");
            Collection<?> all = new ArrayList<>((Collection<?>) getAll.invoke(goals, mob));
            for (Object goal : all) {
                Object key = getKey.invoke(goal);
                NamespacedKey nk = key == null ? null : (NamespacedKey) getNamespaced.invoke(key);
                if (nk != null && names.contains(nk.getKey())) {
                    remove.invoke(goals, mob, goal);
                }
            }
        } catch (Throwable t) {
            log.warning("Could not adjust mob AI goals (" + t.getClass().getSimpleName() + "): " + t.getMessage());
        }
    }
}
