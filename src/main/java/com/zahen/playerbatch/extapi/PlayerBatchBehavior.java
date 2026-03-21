package com.zahen.playerbatch.extapi;

import java.util.Objects;

public record PlayerBatchBehavior(
        String id,
        String displayName,
        String description
) {
    public PlayerBatchBehavior {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        description = description == null ? "" : description;
    }
}
