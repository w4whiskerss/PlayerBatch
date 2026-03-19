package com.zahen.playersummonbulk;

import carpet.CarpetServer;
import com.zahen.playersummonbulk.core.PlayerBatchService;
import com.zahen.playersummonbulk.config.PlayerSummonConfig;
import com.zahen.playersummonbulk.item.SelectionWandItem;
import com.zahen.playersummonbulk.network.PlayerBatchNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerSummonBulk implements ModInitializer {
    public static final String MOD_ID = "playersummonbulk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final CarpetExtensionImpl EXTENSION = new CarpetExtensionImpl();

    @Override
    public void onInitialize() {
        PlayerSummonConfig.load();
        PlayerBatchNetworking.registerCommon();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!SelectionWandItem.isSelectionWand(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            return PlayerBatchService.toggleSelection(serverPlayer, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        CarpetServer.manageExtension(EXTENSION);
        LOGGER.info("Registered {} as a Carpet extension", MOD_ID);
    }
}
