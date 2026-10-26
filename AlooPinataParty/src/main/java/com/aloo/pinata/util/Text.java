package com.aloo.pinata.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Colour handling: &-codes and &#rrggbb hex. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }
}
