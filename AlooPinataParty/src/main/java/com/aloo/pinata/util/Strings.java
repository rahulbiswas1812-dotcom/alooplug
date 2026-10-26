package com.aloo.pinata.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure string helpers (no Bukkit dependency, unit-testable). */
public final class Strings {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.*]{1,32}");

    private Strings() {
    }

    /** Replaces %key% with value for each key/value pair. */
    public static String replace(String template, String... kv) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("%" + kv[i] + "%", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return out;
    }

    public static String formatTime(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.ROOT, "%02d:%02d", m, sec);
    }

    public static String formatDamage(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    public static String formatNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    /** Player names are only [A-Za-z0-9_.*]; anything else becomes '_'. */
    public static String safeName(String name) {
        if (name == null) {
            return "?";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9_.*]", "_");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String l = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }
}
