package com.zahen.playerbatch.extapi;

import java.util.List;
import java.util.Objects;

public record PlayerBatchAction(
        String id,
        String displayName,
        String description,
        List<String> aliases
) {
    public PlayerBatchAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        description = description == null ? "" : description;
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
