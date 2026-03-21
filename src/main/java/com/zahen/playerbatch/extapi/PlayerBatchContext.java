package com.zahen.playerbatch.extapi;

import java.util.Map;

public record PlayerBatchContext(
        String playerBatchVersion,
        int maxSummonCount,
        int spawnsPerTick,
        boolean debugEnabled,
        Map<String, String> metadata
) {
}
