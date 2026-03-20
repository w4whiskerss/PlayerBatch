package com.zahen.playerbatch.core;

import java.util.Locale;

public enum BotAiMode {
    IDLE,
    COMBAT,
    PATROL,
    GUARD,
    FOLLOW,
    FLEE;

    public static BotAiMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
