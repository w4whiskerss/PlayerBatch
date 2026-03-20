package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlayerBatchScreen extends Screen {
    private final Screen parent;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.snapshot = PlayerBatchClient.latestSnapshot();
    }

    @Override
    protected void init() {
        clearWidgets();
        int centerX = width / 2;
        int panelLeft = centerX - 180;
        int buttonTop = height - 54;

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(panelLeft, buttonTop, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh State"), button -> requestState(false))
                .bounds(panelLeft + 118, buttonTop, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reopen Placeholder"), button -> requestState(true))
                .bounds(panelLeft + 236, buttonTop, 124, 20).build());

        requestState(false);
    }

    public void applySnapshot(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void onClose() {
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xF0111418, 0xF01A2028);
        guiGraphics.fill(width / 2 - 190, 34, width / 2 + 190, height - 20, 0xB0141A20);

        int left = width / 2 - 170;
        int top = 52;

        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawString(font, "GUI removed for redesign.", left, top, 0xEBDCA9);
        guiGraphics.drawString(font, "We are rebuilding PlayerBatch UI cleanly instead of extending the old screen.", left, top + 20, 0xC3CED7);
        guiGraphics.drawString(font, "Use commands for now while the new GUI is being designed.", left, top + 36, 0xC3CED7);

        guiGraphics.drawString(font, "Current status", left, top + 70, 0x9BE5B8);
        guiGraphics.drawString(font, "Batch active: " + snapshot.batchActive(), left, top + 90, 0xFFFFFF);
        guiGraphics.drawString(font, "Queued batches: " + snapshot.queuedBatches(), left, top + 106, 0xFFFFFF);
        guiGraphics.drawString(font, "Summon progress: " + snapshot.successCount() + "/" + snapshot.totalCount()
                + " success, " + snapshot.failCount() + " failed, " + snapshot.pendingCount() + " pending", left, top + 122, 0xFFFFFF);
        guiGraphics.drawString(font, "Selected bots: " + snapshot.selectedNames().size(), left, top + 138, 0xFFFFFF);
        guiGraphics.drawString(font, "Groups: " + snapshot.groups().size(), left, top + 154, 0xFFFFFF);

        guiGraphics.drawString(font, "Command examples", left, top + 190, 0x9BE5B8);
        guiGraphics.drawString(font, "/pb summon <count> {optional,names}", left, top + 210, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb select all", left, top + 226, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb customize item <slot> <item> [count]", left, top + 242, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb customize effect <effect> <seconds> [amplifier]", left, top + 258, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb command <action>", left, top + 274, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb group create <name>", left, top + 290, 0xEBDCA9);
        guiGraphics.drawString(font, "/pb ai set <mode>", left, top + 306, 0xEBDCA9);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void requestState(boolean openScreen) {
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.REQUEST_STATE,
                "",
                "",
                0,
                openScreen
        ));
    }

    private void send(PlayerBatchNetworking.PlayerBatchActionPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(payload);
        }
    }
}
