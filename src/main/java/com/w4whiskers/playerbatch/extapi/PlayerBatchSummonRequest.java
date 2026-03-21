package com.w4whiskers.playerbatch.extapi;

import java.util.List;
import java.util.Map;

public record PlayerBatchSummonRequest(
        int count,
        List<String> requestedNames,
        String formationId,
        Map<String, String> argumentValues
) {
}
