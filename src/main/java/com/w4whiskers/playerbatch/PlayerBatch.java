package com.w4whiskers.playerbatch;

import carpet.CarpetServer;
import com.w4whiskers.playerbatch.core.PlayerBatchService;
import com.w4whiskers.playerbatch.config.PlayerBatchConfig;
import com.w4whiskers.playerbatch.ext.PlayerBatchExtensionManager;
import com.w4whiskers.playerbatch.item.SelectionWandItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
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
        PlayerBatchExtensionManager.initialize();
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
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!SelectionWandItem.isSelectionWand(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            return PlayerBatchService.handleWandPointA(serverPlayer, hitResult.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!SelectionWandItem.isSelectionWand(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            return PlayerBatchService.handleWandPointB(serverPlayer, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> PlayerBatchService.cleanupManagedBot(entity));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerBatchService.handlePlayerDisconnect(handler.player));
        CarpetServer.manageExtension(EXTENSION);
        LOGGER.info("Registered {} as a Carpet extension", MOD_ID);
    }
}

