package com.zahen.playerbatch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlayerBatchSummonLoadoutScreen extends Screen {
    private final Screen previousScreen;

    public PlayerBatchSummonLoadoutScreen(Screen previousScreen) {
        super(Component.literal("PlayerBatch Summon Wizard"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int left = (width - 360) / 2;
        int top = (height - 180) / 2;

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> Minecraft.getInstance().setScreen(previousScreen))
                .bounds(left, top + 128, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 280, top + 128, 80, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previousScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xF0111418, 0xF01A2028);

        int left = (width - 360) / 2;
        int top = (height - 180) / 2;
        guiGraphics.fill(left, top, left + 360, top + 180, 0xC0151D25);
        guiGraphics.fill(left + 10, top + 10, left + 350, top + 170, 0xA01C2630);

        guiGraphics.drawCenteredString(font, title, width / 2, top + 18, 0xFFFFFF);
        guiGraphics.drawString(font, "Step 2 placeholder", left + 20, top + 48, 0xEBDCA9);
        guiGraphics.drawString(font, "Continue now opens a real next screen.", left + 20, top + 72, 0xC3CED7);
        guiGraphics.drawString(font, "Page 2 loadout work is still pending.", left + 20, top + 88, 0xC3CED7);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
