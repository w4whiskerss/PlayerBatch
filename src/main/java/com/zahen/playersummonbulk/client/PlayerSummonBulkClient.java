package com.zahen.playersummonbulk.client;

import com.zahen.playersummonbulk.client.gui.PlayerBatchScreen;
import com.zahen.playersummonbulk.core.PlayerBatchService;
import com.zahen.playersummonbulk.network.PlayerBatchNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class PlayerSummonBulkClient implements ClientModInitializer {
    private static PlayerBatchService.PlayerBatchSnapshot latestSnapshot = new PlayerBatchService.PlayerBatchSnapshot(
            false, 256, 4, false, false, 0, 0, 0, 0, 0, java.util.List.of()
    );

    @Override
    public void onInitializeClient() {
        PlayerBatchNetworking.registerClient();
    }

    public static void receiveState(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        latestSnapshot = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot.openScreen() && !(minecraft.screen instanceof PlayerBatchScreen)) {
            minecraft.setScreen(new PlayerBatchScreen(null));
        }
        if (minecraft.screen instanceof PlayerBatchScreen screen) {
            screen.applySnapshot(snapshot);
        }
    }

    public static PlayerBatchService.PlayerBatchSnapshot latestSnapshot() {
        return latestSnapshot;
    }
}
