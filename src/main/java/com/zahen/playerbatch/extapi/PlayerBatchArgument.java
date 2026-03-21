package com.zahen.playerbatch.extapi;

import java.util.List;
import java.util.Objects;

public record PlayerBatchArgument(
        String key,
        String displayName,
        String description,
        PlayerBatchArgumentValueType valueType,
        List<String> suggestionExamples
) {
    public PlayerBatchArgument {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(valueType, "valueType");
        description = description == null ? "" : description;
        suggestionExamples = suggestionExamples == null ? List.of() : List.copyOf(suggestionExamples);
    }
}
