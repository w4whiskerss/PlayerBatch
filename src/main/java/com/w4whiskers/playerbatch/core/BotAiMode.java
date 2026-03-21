package com.w4whiskers.playerbatch.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public static EnumSet<BotAiMode> parseSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.of(IDLE);
        }
        EnumSet<BotAiMode> modes = EnumSet.noneOf(BotAiMode.class);
        for (String token : raw.trim().toLowerCase(Locale.ROOT).split("[+,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            BotAiMode mode = fromString(token);
            if (mode == null) {
                return null;
            }
            modes.add(mode);
        }
        if (modes.isEmpty()) {
            return EnumSet.of(IDLE);
        }
        if (modes.size() > 1) {
            modes.remove(IDLE);
        }
        return modes.isEmpty() ? EnumSet.of(IDLE) : modes;
    }

    public static String displayModes(Set<BotAiMode> modes) {
        if (modes == null || modes.isEmpty()) {
            return IDLE.displayName();
        }
        List<String> labels = new ArrayList<>();
        for (BotAiMode mode : BotAiMode.values()) {
            if (modes.contains(mode)) {
                labels.add(mode.displayName());
            }
        }
        return String.join(" + ", labels);
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
