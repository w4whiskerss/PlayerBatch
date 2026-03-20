package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ActionEditorScreen extends Screen {
    private static final List<String> ACTION_TYPES = List.of("none", "attack", "jump", "use", "stop", "sneak", "unsneak", "sprint", "unsprint", "turn left", "turn right", "look north", "look south", "look east", "look west", "look up", "look down");
    private static final List<String> ACTION_MODES = List.of("none", "once", "continuous");

    private final Screen parent;
    private final List<ActionRow> rows = new ArrayList<>();

    public ActionEditorScreen(Screen parent) {
        super(Component.literal("PlayerBatch Action Editor"));
        this.parent = parent;
        rows.add(new ActionRow(1, 0));
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 170;
        int top = 40;

        addRenderableWidget(Button.builder(Component.literal("+ Add Action"), button -> {
            if (rows.size() < 6) {
                rows.add(new ActionRow(1, 0));
                init();
            }
        }).bounds(left, top, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyActions()).bounds(left + 108, top, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> Minecraft.getInstance().setScreen(parent)).bounds(left + 196, top, 80, 20).build());

        for (int index = 0; index < rows.size(); index++) {
            ActionRow row = rows.get(index);
            int rowTop = top + 32 + index * 28;
            addRenderableWidget(Button.builder(Component.literal("Action: " + ACTION_TYPES.get(row.actionIndex)), button -> {
                row.actionIndex = (row.actionIndex + 1) % ACTION_TYPES.size();
                init();
            }).bounds(left, rowTop, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Mode: " + ACTION_MODES.get(row.modeIndex)), button -> {
                row.modeIndex = (row.modeIndex + 1) % ACTION_MODES.size();
                init();
            }).bounds(left + 158, rowTop, 110, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove"), button -> {
                rows.remove(row);
                if (rows.isEmpty()) {
                    rows.add(new ActionRow(1, 0));
                }
                init();
            }).bounds(left + 276, rowTop, 64, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xA0101010);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawString(font, "Build multiple selected-bot actions here, then apply them as one action set.", width / 2 - 170, 28, 0xC3CED7);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void applyActions() {
        List<String> commands = new ArrayList<>();
        for (ActionRow row : rows) {
            String command = row.command();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }
        if (commands.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                    PlayerBatchNetworking.ActionKind.RUN_ACTION_SET,
                    String.join("\n", commands),
                    "",
                    0,
                    false
            ));
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private static final class ActionRow {
        private int actionIndex;
        private int modeIndex;

        private ActionRow(int actionIndex, int modeIndex) {
            this.actionIndex = actionIndex;
            this.modeIndex = modeIndex;
        }

        private String command() {
            String action = ACTION_TYPES.get(actionIndex);
            String mode = ACTION_MODES.get(modeIndex);
            if ("none".equals(action)) {
                return "";
            }
            if (action.startsWith("look ") || action.startsWith("turn ") || List.of("stop", "sneak", "unsneak", "sprint", "unsprint").contains(action)) {
                return action;
            }
            return "none".equals(mode) ? action : action + " " + mode;
        }
    }
}
