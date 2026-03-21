package com.w4whiskers.playerbatch.extapi;

import java.util.Objects;

public record PlayerBatchBehavior(
        String id,
        String displayName,
        String description,
        PlayerBatchBehaviorHandler handler
) {
    public PlayerBatchBehavior {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(handler, "handler");
        description = description == null ? "" : description;
    }
}
