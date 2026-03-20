package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class LoadoutEditorScreen extends Screen {
    private static final List<SlotRef> SLOT_REFS = List.of(
            new SlotRef("Head", "head", -1),
            new SlotRef("Chest", "chest", -1),
            new SlotRef("Legs", "legs", -1),
            new SlotRef("Feet", "feet", -1),
            new SlotRef("Main", "mainhand", -1),
            new SlotRef("Off", "offhand", -1),
            new SlotRef("1", null, 0),
            new SlotRef("2", null, 1),
            new SlotRef("3", null, 2),
            new SlotRef("4", null, 3),
            new SlotRef("5", null, 4),
            new SlotRef("6", null, 5),
            new SlotRef("7", null, 6),
            new SlotRef("8", null, 7),
            new SlotRef("9", null, 8)
    );

    private final Screen parent;
    private int selectedIndex;
    private EditBox itemBox;
    private EditBox countBox;

    public LoadoutEditorScreen(Screen parent) {
        super(Component.literal("PlayerBatch Loadout Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 170;
        int top = 38;

        for (int index = 0; index < 6; index++) {
            int x = left + index * 56;
            int slotIndex = index;
            addRenderableWidget(Button.builder(slotLabel(slotIndex), button -> {
                selectedIndex = slotIndex;
                init();
            }).bounds(x, top, 50, 20).build());
        }
        for (int index = 0; index < 9; index++) {
            int x = left + index * 36;
            int slotIndex = 6 + index;
            addRenderableWidget(Button.builder(slotLabel(slotIndex), button -> {
                selectedIndex = slotIndex;
                init();
            }).bounds(x, top + 34, 30, 20).build());
        }

        itemBox = addRenderableWidget(new EditBox(font, left, top + 78, 190, 20, Component.literal("Item")));
        countBox = addRenderableWidget(new EditBox(font, left + 198, top + 78, 50, 20, Component.literal("Count")));
        countBox.setValue("1");
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applySelection()).bounds(left + 256, top + 78, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Slot"), button -> clearSelection()).bounds(left, top + 108, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Deselect All"), button -> clearSelectionSet()).bounds(left + 100, top + 108, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> Minecraft.getInstance().setScreen(parent)).bounds(left + 212, top + 108, 84, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xA0101010);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawString(font, "Visual editor for selected bots: armor, hands, and hotbar.", width / 2 - 170, 26, 0xC3CED7);
        guiGraphics.drawString(font, "Selected slot: " + SLOT_REFS.get(selectedIndex).displayName(), width / 2 - 170, 62, 0x9BE5B8);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private Component slotLabel(int index) {
        SlotRef ref = SLOT_REFS.get(index);
        return Component.literal((selectedIndex == index ? "> " : "") + ref.displayName());
    }

    private void applySelection() {
        String itemId = itemBox.getValue().trim();
        int count = parseInt(countBox.getValue(), 1);
        if (itemId.isEmpty() || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        SlotRef ref = SLOT_REFS.get(selectedIndex);
        if (ref.hotbarIndex() >= 0) {
            ClientPlayNetworking.send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                    PlayerBatchNetworking.ActionKind.APPLY_SELECTED_HOTBAR_SLOT,
                    normalize(itemId),
                    Integer.toString(count),
                    ref.hotbarIndex(),
                    false
            ));
        } else {
            ClientPlayNetworking.send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                    PlayerBatchNetworking.ActionKind.APPLY_SELECTED_ITEM,
                    ref.slotName(),
                    normalize(itemId),
                    count,
                    false
            ));
        }
    }

    private void clearSelection() {
        itemBox.setValue("minecraft:air");
        applySelection();
    }

    private void clearSelectionSet() {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                    PlayerBatchNetworking.ActionKind.CLEAR_SELECTION, "", "", 0, false
            ));
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalize(String raw) {
        return raw.contains(":") ? raw.toLowerCase() : "minecraft:" + raw.toLowerCase();
    }

    private record SlotRef(String displayName, String slotName, int hotbarIndex) {
    }
}
