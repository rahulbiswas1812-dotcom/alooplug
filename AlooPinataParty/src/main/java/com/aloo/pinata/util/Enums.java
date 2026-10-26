package com.aloo.pinata.util;

import java.util.Locale;

public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
