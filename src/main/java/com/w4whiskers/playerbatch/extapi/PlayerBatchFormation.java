package com.w4whiskers.playerbatch.extapi;

import java.util.Objects;

public record PlayerBatchFormation(
        String id,
        String displayName,
        String description,
        PlayerBatchFormationFactory factory
) {
    public PlayerBatchFormation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(factory, "factory");
        description = description == null ? "" : description;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private String description;
        private PlayerBatchFormationFactory factory;

        private Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder factory(PlayerBatchFormationFactory factory) {
            this.factory = factory;
            return this;
        }

        public PlayerBatchFormation build() {
            return new PlayerBatchFormation(id, displayName, description, factory);
        }
    }
}
