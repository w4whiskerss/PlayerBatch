package com.w4whiskers.playerbatch.extapi;

@FunctionalInterface
public interface PlayerBatchBehaviorHandler {
    void apply(PlayerBatchBotController bot, PlayerBatchSummonPlan plan, PlayerBatchContext context);
}
