package com.zahen.playersummonbulk.network;

import com.zahen.playersummonbulk.PlayerSummonBulk;
import com.zahen.playersummonbulk.client.PlayerSummonBulkClient;
import com.zahen.playersummonbulk.core.PlayerBatchService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class PlayerBatchNetworking {
    private PlayerBatchNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(PlayerBatchActionPayload.TYPE, PlayerBatchActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerBatchStatePayload.TYPE, PlayerBatchStatePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PlayerBatchActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> handleServerAction(context.player(), payload)));
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerBatchStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> PlayerSummonBulkClient.receiveState(payload.snapshot())));
    }

    public static void sendState(ServerPlayer player, PlayerBatchService.PlayerBatchSnapshot snapshot) {
        ServerPlayNetworking.send(player, new PlayerBatchStatePayload(snapshot));
    }

    private static void handleServerAction(ServerPlayer player, PlayerBatchActionPayload payload) {
        switch (payload.kind()) {
            case REQUEST_STATE -> PlayerBatchService.requestState(player, payload.flag());
            case CLOSE_SCREEN -> PlayerBatchService.handleGuiClosed(player);
            case SET_LIMIT -> PlayerBatchService.applyLimitFromGui(player, payload.number());
            case SET_SPAWNS_PER_TICK -> PlayerBatchService.applySpawnsPerTickFromGui(player, payload.number());
            case SET_DEBUG -> PlayerBatchService.applyDebugFromGui(player, payload.flag());
            case SUMMON -> PlayerBatchService.requestSummonFromGui(player, payload.number(), payload.text());
            case RUN_ACTION -> PlayerBatchService.runSelectedActionFromGui(player, payload.text());
            case TELEPORT_SELECTION -> PlayerBatchService.teleportSelectionFromGui(player, payload.text(), payload.number());
            case CLEAR_SELECTION -> PlayerBatchService.clearSelectionFromGui(player);
            case GIVE_WAND -> PlayerBatchService.giveWandFromGui(player);
        }
    }

    public enum ActionKind {
        REQUEST_STATE,
        CLOSE_SCREEN,
        SET_LIMIT,
        SET_SPAWNS_PER_TICK,
        SET_DEBUG,
        SUMMON,
        RUN_ACTION,
        TELEPORT_SELECTION,
        CLEAR_SELECTION,
        GIVE_WAND
    }

    public record PlayerBatchActionPayload(ActionKind kind, String text, int number, boolean flag) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerBatchActionPayload> TYPE =
                CustomPacketPayload.createType(PlayerSummonBulk.MOD_ID + "_action");
        public static final StreamCodec<FriendlyByteBuf, PlayerBatchActionPayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> payload.write(buffer), PlayerBatchActionPayload::new);

        public PlayerBatchActionPayload(FriendlyByteBuf buffer) {
            this(ActionKind.values()[buffer.readVarInt()], buffer.readUtf(), buffer.readVarInt(), buffer.readBoolean());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(kind.ordinal());
            buffer.writeUtf(text);
            buffer.writeVarInt(number);
            buffer.writeBoolean(flag);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlayerBatchStatePayload(PlayerBatchService.PlayerBatchSnapshot snapshot) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerBatchStatePayload> TYPE =
                CustomPacketPayload.createType(PlayerSummonBulk.MOD_ID + "_state");
        public static final StreamCodec<FriendlyByteBuf, PlayerBatchStatePayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> payload.write(buffer), PlayerBatchStatePayload::new);

        public PlayerBatchStatePayload(FriendlyByteBuf buffer) {
            this(readSnapshot(buffer));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(snapshot.openScreen());
            buffer.writeVarInt(snapshot.maxSummonCount());
            buffer.writeVarInt(snapshot.maxSpawnsPerTick());
            buffer.writeBoolean(snapshot.debugEnabled());
            buffer.writeBoolean(snapshot.batchActive());
            buffer.writeVarInt(snapshot.totalCount());
            buffer.writeVarInt(snapshot.successCount());
            buffer.writeVarInt(snapshot.failCount());
            buffer.writeVarInt(snapshot.pendingCount());
            buffer.writeVarInt(snapshot.queuedBatches());
            buffer.writeVarInt(snapshot.selectedNames().size());
            for (String selectedName : snapshot.selectedNames()) {
                buffer.writeUtf(selectedName);
            }
        }

        private static PlayerBatchService.PlayerBatchSnapshot readSnapshot(FriendlyByteBuf buffer) {
            boolean openScreen = buffer.readBoolean();
            int maxSummonCount = buffer.readVarInt();
            int maxSpawnsPerTick = buffer.readVarInt();
            boolean debugEnabled = buffer.readBoolean();
            boolean batchActive = buffer.readBoolean();
            int totalCount = buffer.readVarInt();
            int successCount = buffer.readVarInt();
            int failCount = buffer.readVarInt();
            int pendingCount = buffer.readVarInt();
            int queuedBatches = buffer.readVarInt();
            int selectedSize = buffer.readVarInt();
            List<String> selectedNames = new ArrayList<>();
            for (int index = 0; index < selectedSize; index++) {
                selectedNames.add(buffer.readUtf());
            }
            return new PlayerBatchService.PlayerBatchSnapshot(
                    openScreen,
                    maxSummonCount,
                    maxSpawnsPerTick,
                    debugEnabled,
                    batchActive,
                    totalCount,
                    successCount,
                    failCount,
                    pendingCount,
                    queuedBatches,
                    selectedNames
            );
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
