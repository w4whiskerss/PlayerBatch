package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PlayerBatchScreen extends Screen {
    private final Screen parent;

    private EditBox countBox;
    private EditBox namesBox;
    private EditBox limitBox;
    private EditBox perTickBox;
    private EditBox directionBox;
    private EditBox blocksBox;
    private Button debugButton;
    private LimitSlider limitSlider;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.snapshot = PlayerBatchClient.latestSnapshot();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = 32;

        limitSlider = addRenderableWidget(new LimitSlider(centerX - 155, startY, 150, 20, snapshot.maxSummonCount()));
        limitBox = addRenderableWidget(new EditBox(font, centerX + 5, startY, 60, 20, Component.literal("Limit")));
        limitBox.setValue(Integer.toString(snapshot.maxSummonCount()));
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyLimit()).bounds(centerX + 70, startY, 80, 20).build());

        perTickBox = addRenderableWidget(new EditBox(font, centerX - 155, startY + 28, 60, 20, Component.literal("Spawns/Tick")));
        perTickBox.setValue(Integer.toString(snapshot.maxSpawnsPerTick()));
        addRenderableWidget(Button.builder(Component.literal("Set Tick Rate"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_SPAWNS_PER_TICK,
                "",
                parseInt(perTickBox.getValue(), snapshot.maxSpawnsPerTick()),
                false
        ))).bounds(centerX - 90, startY + 28, 120, 20).build());

        debugButton = addRenderableWidget(Button.builder(debugLabel(), button -> {
            boolean newDebug = !snapshot.debugEnabled();
            send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                    PlayerBatchNetworking.ActionKind.SET_DEBUG,
                    "",
                    0,
                    newDebug
            ));
        }).bounds(centerX + 35, startY + 28, 115, 20).build());

        countBox = addRenderableWidget(new EditBox(font, centerX - 155, startY + 64, 60, 20, Component.literal("Count")));
        countBox.setValue("1");
        namesBox = addRenderableWidget(new EditBox(font, centerX - 90, startY + 64, 240, 20, Component.literal("Names")));
        namesBox.setHint(Component.literal("{NameOne, NameTwo}"));

        addRenderableWidget(Button.builder(Component.literal("Summon"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SUMMON,
                namesBox.getValue(),
                parseInt(countBox.getValue(), 1),
                false
        ))).bounds(centerX - 155, startY + 92, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Get Wand"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.GIVE_WAND, "", 0, false
        ))).bounds(centerX - 60, startY + 92, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear Selected"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.CLEAR_SELECTION, "", 0, false
        ))).bounds(centerX + 35, startY + 92, 115, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Hit Once"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.RUN_ACTION, "attack once", 0, false
        ))).bounds(centerX - 155, startY + 126, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Jump"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.RUN_ACTION, "jump once", 0, false
        ))).bounds(centerX - 60, startY + 126, 90, 20).build());

        directionBox = addRenderableWidget(new EditBox(font, centerX + 35, startY + 126, 55, 20, Component.literal("Dir")));
        directionBox.setValue("up");
        blocksBox = addRenderableWidget(new EditBox(font, centerX + 95, startY + 126, 25, 20, Component.literal("Blocks")));
        blocksBox.setValue("5");
        addRenderableWidget(Button.builder(Component.literal("TP"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.TELEPORT_SELECTION,
                directionBox.getValue(),
                parseInt(blocksBox.getValue(), 5),
                false
        ))).bounds(centerX + 123, startY + 126, 27, 20).build());

        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.REQUEST_STATE, "", 0, false));
        applySnapshot(snapshot);
    }

    public void applySnapshot(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        this.snapshot = snapshot;
        if (limitBox != null && !limitBox.isFocused()) {
            limitBox.setValue(Integer.toString(snapshot.maxSummonCount()));
        }
        if (perTickBox != null && !perTickBox.isFocused()) {
            perTickBox.setValue(Integer.toString(snapshot.maxSpawnsPerTick()));
        }
        if (limitSlider != null) {
            limitSlider.setValueFromLimit(snapshot.maxSummonCount());
        }
        if (debugButton != null) {
            debugButton.setMessage(debugLabel());
        }
    }

    @Override
    public void onClose() {
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        guiGraphics.drawString(font, "Live config and summon controls", width / 2 - 155, 18, 0xB0B0B0);
        guiGraphics.drawString(font, progressText(), width / 2 - 155, 160, 0xFFFFFF);
        guiGraphics.drawString(font, "Selected bots (" + snapshot.selectedNames().size() + "):", width / 2 - 155, 178, 0xFFFFFF);

        int y = 192;
        if (snapshot.selectedNames().isEmpty()) {
            guiGraphics.drawString(font, "None", width / 2 - 155, y, 0x808080);
        } else {
            for (String selectedName : snapshot.selectedNames()) {
                guiGraphics.drawString(font, "- " + selectedName, width / 2 - 155, y, 0xA6E3FF);
                y += 10;
                if (y > height - 20) {
                    break;
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void applyLimit() {
        int value = parseInt(limitBox.getValue(), snapshot.maxSummonCount());
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.SET_LIMIT, "", value, false));
    }

    private Component debugLabel() {
        return Component.literal("Debug: " + (snapshot.debugEnabled() ? "ON" : "OFF"));
    }

    private String progressText() {
        if (!snapshot.batchActive()) {
            return "Queue idle. Queued batches: " + snapshot.queuedBatches();
        }
        return "Summoning " + snapshot.successCount() + "/" + snapshot.totalCount()
                + " success, " + snapshot.failCount() + " failed, "
                + snapshot.pendingCount() + " pending";
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void send(PlayerBatchNetworking.PlayerBatchActionPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(payload);
        }
    }

    private static final class LimitSlider extends AbstractSliderButton {
        private static final int MIN_LIMIT = 1;
        private static final int MAX_LIMIT = 512;

        private LimitSlider(int x, int y, int width, int height, int initialLimit) {
            super(x, y, width, height, Component.empty(), 0.0);
            setValueFromLimit(initialLimit);
        }

        private void setValueFromLimit(int limit) {
            value = (double) (Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit)) - MIN_LIMIT) / (double) (MAX_LIMIT - MIN_LIMIT);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int currentLimit = MIN_LIMIT + (int) Math.round(value * (MAX_LIMIT - MIN_LIMIT));
            setMessage(Component.literal("Limit Slider: " + currentLimit));
        }

        @Override
        protected void applyValue() {
            if (Minecraft.getInstance().screen instanceof PlayerBatchScreen screen && screen.limitBox != null) {
                int currentLimit = MIN_LIMIT + (int) Math.round(value * (MAX_LIMIT - MIN_LIMIT));
                screen.limitBox.setValue(Integer.toString(currentLimit));
            }
        }
    }
}

