package com.zahen.playerbatch.extapi;

import java.util.Collection;

public interface PlayerBatchRegistrar {
    void registerFormation(PlayerBatchFormation formation);

    void registerArgument(PlayerBatchArgument argument);

    void registerAction(PlayerBatchAction action);

    void registerBehavior(PlayerBatchBehavior behavior);

    Collection<PlayerBatchFormation> formations();

    Collection<PlayerBatchArgument> arguments();

    Collection<PlayerBatchAction> actions();

    Collection<PlayerBatchBehavior> behaviors();
}
