package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.client.PlayerBatchUiPreferences;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlayerBatchScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 250;

    private final Screen parent;
    private final PlayerBatchUiPreferences preferences;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    private EditBox countBox;
    private EditBox namesBox;
    private Button nextButton;
    private ValidationSummary summary = ValidationSummary.empty();
    private String infoMessage;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch Summon Wizard"));
        this.parent = parent;
        this.preferences = PlayerBatchUiPreferences.load();
        this.snapshot = PlayerBatchClient.latestSnapshot();
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = panelLeft();
        int top = panelTop();

        countBox = addRenderableWidget(new EditBox(font, left + 20, top + 56, 80, 20, Component.literal("Bot count")));
        countBox.setValue(sanitizeCount(preferences.summonCount()));
        countBox.setResponder(value -> {
            saveDraft();
            refreshSummary();
        });

        namesBox = addRenderableWidget(new EditBox(font, left + 20, top + 104, PANEL_WIDTH - 40, 20, Component.literal("Usernames")));
        namesBox.setValue(preferences.summonNames());
        namesBox.setResponder(value -> {
            autoGrowCount();
            saveDraft();
            refreshSummary();
        });

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> closeKeepingDraft())
                .bounds(left + 20, top + PANEL_HEIGHT - 34, 84, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("Continue"), button -> continueToNextStep())
                .bounds(left + PANEL_WIDTH - 214, top + PANEL_HEIGHT - 34, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> closeKeepingDraft())
                .bounds(left + PANEL_WIDTH - 88, top + PANEL_HEIGHT - 34, 68, 20).build());

        refreshSummary();
        requestState(false);
    }

    public void applySnapshot(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void onClose() {
        closeKeepingDraft();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xF0111418, 0xF01A2028);

        int left = panelLeft();
        int top = panelTop();
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xC0151D25);
        guiGraphics.fill(left + 10, top + 10, left + PANEL_WIDTH - 10, top + PANEL_HEIGHT - 10, 0xA01C2630);

        guiGraphics.drawCenteredString(font, title, width / 2, top + 16, 0xFFFFFF);
        guiGraphics.drawString(font, "Step 1 of 3: usernames and count", left + 20, top + 36, 0xEBDCA9);
        guiGraphics.drawString(font, "Enter specific bot names here. Remaining bots stay random every summon batch.", left + 20, top + 76, 0xC3CED7);
        guiGraphics.drawString(font, "Names: comma-separated, like Alice, Bob, Charlie", left + 20, top + 92, 0x8FB7D1);

        guiGraphics.drawString(font, "Live preview", left + 20, top + 138, 0x9BE5B8);
        guiGraphics.drawString(font, "Custom names: " + summary.customNameCount(), left + 20, top + 156, 0xFFFFFF);
        guiGraphics.drawString(font, "Random bots: " + summary.randomBotCount, left + 160, top + 156, 0xFFFFFF);
        guiGraphics.drawString(font, "Final count: " + summary.effectiveCount, left + 280, top + 156, 0xFFFFFF);
        guiGraphics.drawString(font, "Parsed names: " + summary.previewNames(), left + 20, top + 168, 0xD9E4F1);

        int statusColor = summary.errorMessage == null ? 0xBFD8E8 : 0xFF9696;
        String statusText = summary.errorMessage == null
                ? (infoMessage != null ? infoMessage : "Draft saved. Taken names fall back to random names during summon.")
                : summary.errorMessage;
        guiGraphics.drawString(font, statusText, left + 20, top + 188, statusColor);

        guiGraphics.drawString(font, "Batch status: " + (snapshot.batchActive() ? "running" : "idle")
                + " | queued " + snapshot.queuedBatches()
                + " | selected " + snapshot.selectedNames().size(), left + 20, top + 206, 0xC3CED7);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void continueToNextStep() {
        saveDraft();
        refreshSummary();
        if (!summary.canContinue) {
            return;
        }
        Minecraft.getInstance().setScreen(new PlayerBatchSummonLoadoutScreen(this));
    }

    private void closeKeepingDraft() {
        saveDraft();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshSummary() {
        autoGrowCount();
        summary = summarize();
        if (summary.errorMessage != null) {
            infoMessage = null;
        }
        if (nextButton != null) {
            nextButton.active = summary.canContinue;
            nextButton.setMessage(Component.literal(summary.buttonLabel()));
        }
    }

    private ValidationSummary summarize() {
        List<String> parsedNames = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String duplicate = null;
        String invalid = null;

        String raw = namesBox == null ? preferences.summonNames() : namesBox.getValue();
        for (String piece : raw.split(",")) {
            String candidate = piece.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!candidate.matches("[A-Za-z0-9_]{3,16}")) {
                invalid = candidate;
                break;
            }
            String lowered = candidate.toLowerCase(Locale.ROOT);
            if (!seen.add(lowered)) {
                duplicate = candidate;
                break;
            }
            parsedNames.add(candidate);
        }

        int requestedCount = parseCount();
        int effectiveCount = Math.max(requestedCount, parsedNames.size());
        int randomCount = Math.max(0, effectiveCount - parsedNames.size());

        if (duplicate != null) {
            return new ValidationSummary(parsedNames, effectiveCount, randomCount, false, "Duplicate username: " + duplicate);
        }
        if (invalid != null) {
            return new ValidationSummary(parsedNames, effectiveCount, randomCount, false, "Invalid username: " + invalid + " (use 3-16 letters, numbers, or _)");
        }
        if (effectiveCount <= 0) {
            return new ValidationSummary(parsedNames, effectiveCount, randomCount, false, "Bot count must be at least 1.");
        }
        return new ValidationSummary(parsedNames, effectiveCount, randomCount, true, null);
    }

    private void autoGrowCount() {
        if (countBox == null || namesBox == null) {
            return;
        }
        int currentCount = parseCount();
        int nameCount = countEnteredNames(namesBox.getValue());
        if (nameCount > currentCount) {
            countBox.setValue(Integer.toString(nameCount));
        }
    }

    private int countEnteredNames(String raw) {
        int count = 0;
        for (String piece : raw.split(",")) {
            if (!piece.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int parseCount() {
        try {
            return Integer.parseInt((countBox == null ? preferences.summonCount() : countBox.getValue()).trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String sanitizeCount(String raw) {
        int parsed = 0;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
        }
        return Integer.toString(Math.max(1, parsed));
    }

    private void setStatusLabel(String text) {
        infoMessage = text;
        if (nextButton != null) {
            nextButton.active = summary.canContinue;
        }
    }

    private void saveDraft() {
        if (countBox != null) {
            preferences.setSummonCount(sanitizeCount(countBox.getValue()));
        }
        if (namesBox != null) {
            preferences.setSummonNames(namesBox.getValue().trim());
        }
        preferences.save();
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

    private int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private record ValidationSummary(
            List<String> parsedNames,
            int effectiveCount,
            int randomBotCount,
            boolean canContinue,
            String errorMessage
    ) {
        private static ValidationSummary empty() {
            return new ValidationSummary(List.of(), 1, 1, true, null);
        }

        private String buttonLabel() {
            return "Continue with " + effectiveCount;
        }

        private int customNameCount() {
            return parsedNames.size();
        }

        private String previewNames() {
            if (parsedNames.isEmpty()) {
                return "No custom names yet.";
            }
            if (parsedNames.size() <= 5) {
                return String.join(", ", parsedNames);
            }
            return String.join(", ", parsedNames.subList(0, 5)) + " ...";
        }
    }
}
