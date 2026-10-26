package com.aloo.pinata.damage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory per-event damage statistics (no disk access, main thread only). */
public final class DamageTracker {

    public record Ranked(int rank, UUID id, String name, double damage, int hits, long participationSeconds) {
    }

    private static final class Stat {
        final UUID id;
        String name;
        double damage;
        int hits;
        final long firstHit;
        long lastHit;

        Stat(UUID id, String name, long now) {
            this.id = id;
            this.name = name;
            this.firstHit = now;
            this.lastHit = now;
        }
    }

    private final Map<UUID, Stat> stats = new HashMap<>();
    private double total;

    public void clear() {
        stats.clear();
        total = 0;
    }

    /** Records real damage. Returns false (and records nothing) for invalid amounts. */
    public boolean record(UUID id, String name, double amount, long nowMs) {
        if (id == null || !Double.isFinite(amount) || amount <= 0) {
            return false;
        }
        Stat s = stats.computeIfAbsent(id, k -> new Stat(k, name, nowMs));
        s.name = name;
        s.damage += amount;
        s.hits++;
        s.lastHit = nowMs;
        total += amount;
        return true;
    }

    public double total() {
        return total;
    }

    public int participants() {
        return stats.size();
    }

    public double damageOf(UUID id) {
        Stat s = stats.get(id);
        return s == null ? 0 : s.damage;
    }

    /** Most damage first; ties go to whoever hit first, then by UUID so the order is always deterministic. */
    public List<Ranked> ranking() {
        List<Stat> sorted = new ArrayList<>(stats.values());
        sorted.sort(Comparator.<Stat>comparingDouble(s -> -s.damage)
                .thenComparingLong(s -> s.firstHit)
                .thenComparing(s -> s.id.toString()));
        List<Ranked> out = new ArrayList<>(sorted.size());
        int rank = 1;
        for (Stat s : sorted) {
            out.add(new Ranked(rank++, s.id, s.name, s.damage, s.hits, Math.max(0, (s.lastHit - s.firstHit) / 1000)));
        }
        return out;
    }
}
