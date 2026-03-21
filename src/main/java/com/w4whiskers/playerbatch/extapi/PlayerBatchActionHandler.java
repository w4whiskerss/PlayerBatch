package com.w4whiskers.playerbatch.extapi;

import java.util.Collection;

@FunctionalInterface
public interface PlayerBatchActionHandler {
    int execute(String rawAction, Collection<PlayerBatchBotController> bots, PlayerBatchContext context);
}
