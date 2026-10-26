package com.aloo.pinata;

import com.aloo.pinata.arena.Arena;
import com.aloo.pinata.arena.BlockPos;
import com.aloo.pinata.damage.DamageTracker;
import com.aloo.pinata.reward.RewardFormatter;
import com.aloo.pinata.util.Strings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicTest {

    private static YamlConfiguration res(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                LogicTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void timeFormatting() {
        assertEquals("09:59", Strings.formatTime(599));
        assertEquals("1:00:00", Strings.formatTime(3600));
        assertEquals("00:00", Strings.formatTime(-1));
    }

    @Test
    void safeNames() {
        assertTrue(Strings.isSafeName("Steve_01"));
        assertFalse(Strings.isSafeName("a b"));
        assertFalse(Strings.isSafeName("x;op"));
    }

    @Test
    void rankingIsDeterministicAndRejectsBadDamage() {
        DamageTracker t = new DamageTracker();
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        assertFalse(t.record(a, "A", -3, 1));
        assertFalse(t.record(a, "A", Double.NaN, 1));
        t.record(b, "B", 10, 100);
        t.record(a, "A", 10, 200);
        var r = t.ranking();
        assertEquals("B", r.get(0).name());   // tie: earliest hit wins
        assertEquals(1, r.get(0).rank());
        assertEquals(20.0, t.total(), 1e-9);
    }

    @Test
    void arenaBounds() {
        Arena a = Arena.of(new BlockPos("w", 10, 70, 10), new BlockPos("w", 0, 60, 0));
        assertTrue(a.containsPoint("w", 0, 60, 0));
        assertTrue(a.containsPoint("w", 10.99, 70.99, 10.99));
        assertFalse(a.containsPoint("w", 11.0, 65, 5));
        assertFalse(a.containsPoint("other", 5, 65, 5));
        assertEquals(11L * 11 * 11, a.volume());
    }

    @Test
    void rewardPlaceholders() {
        assertEquals("eco give Steve 150 12.3 #2 WARDEN e1 u-1",
                RewardFormatter.expand("/eco give %player% %mult:100% %damage% #%rank% %mob% %event_id% %uuid%",
                        "Steve", "u-1", 12.34, 2, "WARDEN", "e1", 1.5));
    }

    @Test
    void defaultMobsAreWithinSpecifiedHealthRange() {
        var mobs = res("mobs.yml").getConfigurationSection("mobs");
        assertNotNull(mobs);
        for (String k : new String[]{"WARDEN", "LLAMA", "ZOMBIE", "HOGLIN", "WITHER_SKELETON"}) {
            double hp = mobs.getDouble(k + ".health");
            assertTrue(hp >= 150 && hp <= 300, k + " health " + hp);
        }
    }

    @Test
    void defaultFilesParse() {
        assertEquals(3.0, res("config.yml").getDouble("schedule.interval-hours"));
        assertEquals(10, res("config.yml").getInt("schedule.announcement-minutes"));
        assertNotNull(res("messages.yml").getString("announce.join-button"));
        assertNotNull(res("rewards.yml").getConfigurationSection("rewards.participation"));
    }
}
