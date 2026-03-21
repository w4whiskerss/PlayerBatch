package com.zahen.playerbatch.extapi;

@FunctionalInterface
public interface PlayerBatchArgumentHandler {
    boolean apply(String token, PlayerBatchSummonPlan plan, PlayerBatchContext context);
}
