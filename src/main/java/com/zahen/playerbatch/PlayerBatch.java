package com.zahen.playerbatch;

import carpet.CarpetServer;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.config.PlayerBatchConfig;
import com.zahen.playerbatch.item.SelectionWandItem;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerBatch implements ModInitializer {
    public static final String MOD_ID = "playerbatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final CarpetExtensionImpl EXTENSION = new CarpetExtensionImpl();

    @Override
    public void onInitialize() {
        PlayerBatchConfig.load();
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
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
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

