package com.aloo.pinata.reward;

import com.aloo.pinata.util.Strings;

import java.util.regex.Pattern;

/** Pure placeholder expansion for reward commands (unit-testable). */
public final class RewardFormatter {

    private static final Pattern MULT = Pattern.compile("%mult:(\\d+(?:\\.\\d+)?)%");

    private RewardFormatter() {
    }

    public static String expand(String template, String player, String uuid, double damage, int rank,
                                String mob, String eventId, double multiplier) {
        String s = template == null ? "" : template.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        s = MULT.matcher(s).replaceAll(m -> Strings.formatNumber(Double.parseDouble(m.group(1)) * multiplier));
        return Strings.replace(s,
                "player", player,
                "uuid", uuid,
                "damage", Strings.formatDamage(damage),
                "rank", Integer.toString(rank),
                "mob", mob,
                "event_id", eventId,
                "multiplier", Strings.formatNumber(multiplier));
    }
}
