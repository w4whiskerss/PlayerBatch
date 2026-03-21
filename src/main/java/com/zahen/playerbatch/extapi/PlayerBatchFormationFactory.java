package com.zahen.playerbatch.extapi;

import java.util.List;

@FunctionalInterface
public interface PlayerBatchFormationFactory {
    List<PlayerBatchSpawnPoint> create(PlayerBatchSummonRequest request, PlayerBatchContext context);
}
