package com.w4whiskers.playerbatch.extapi;

import java.util.UUID;

public interface PlayerBatchBotController {
    UUID uuid();

    String name();

    void addTag(String tag);

    void removeTag(String tag);

    boolean runAction(String action);
}
