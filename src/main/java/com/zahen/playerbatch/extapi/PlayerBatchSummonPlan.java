package com.zahen.playerbatch.extapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerBatchSummonPlan {
    private String formationId = "circle";
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final Set<String> behaviorIds = new LinkedHashSet<>();
    private final List<String> postSpawnActions = new ArrayList<>();

    public String formationId() {
        return formationId;
    }

    public void setFormationId(String formationId) {
        if (formationId != null && !formationId.isBlank()) {
            this.formationId = formationId;
        }
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public void putMetadata(String key, String value) {
        if (key != null && !key.isBlank()) {
            metadata.put(key, value == null ? "" : value);
        }
    }

    public Set<String> behaviorIds() {
        return behaviorIds;
    }

    public void addBehavior(String behaviorId) {
        if (behaviorId != null && !behaviorId.isBlank()) {
            behaviorIds.add(behaviorId);
        }
    }

    public List<String> postSpawnActions() {
        return postSpawnActions;
    }

    public void addPostSpawnAction(String action) {
        if (action != null && !action.isBlank()) {
            postSpawnActions.add(action);
        }
    }

    public PlayerBatchSummonPlan copy() {
        PlayerBatchSummonPlan copy = new PlayerBatchSummonPlan();
        copy.formationId = formationId;
        copy.metadata.putAll(metadata);
        copy.behaviorIds.addAll(behaviorIds);
        copy.postSpawnActions.addAll(postSpawnActions);
        return copy;
    }
}
