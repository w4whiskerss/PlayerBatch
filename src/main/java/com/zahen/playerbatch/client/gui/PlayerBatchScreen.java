package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.core.BotAiMode;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerBatchScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> summoningWidgets = new ArrayList<>();
    private final List<AbstractWidget> customizationWidgets = new ArrayList<>();
    private final List<AbstractWidget> debugWidgets = new ArrayList<>();
    private static final List<String> FORMATION_OPTIONS = List.of("circle", "square", "triangle", "random", "single block");

    private Tab activeTab = Tab.SUMMONING;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    private LimitSlider limitSlider;
    private EditBox countBox;
    private EditBox namesBox;
    private EditBox limitBox;
    private EditBox perTickBox;
    private Button formationButton;
    private int formationIndex = 0;

    private EditBox rangeBox;
    private EditBox closestBox;
    private EditBox groupBox;
    private EditBox aiModeBox;
    private EditBox actionBox;
    private EditBox directionBox;
    private EditBox blockBox;

    private Button debugButton;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.snapshot = PlayerBatchClient.latestSnapshot();
    }

    @Override
    protected void init() {
        clearWidgets();

        int panelLeft = width / 2 - 185;
        int panelTop = 42;
        int panelWidth = 370;

        initTabs(panelLeft, 12, panelWidth);
        initSummoningTab(panelLeft, panelTop);
        initCustomizationTab(panelLeft, panelTop);
        initDebugTab(panelLeft, panelTop);

        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.REQUEST_STATE, "", "", 0, false));
        applySnapshot(snapshot);
        refreshTabVisibility();
    }

    private void initTabs(int left, int top, int width) {
        int buttonWidth = 118;
        addRenderableWidget(Button.builder(Component.literal("Summoning"), button -> switchTab(Tab.SUMMONING))
                .bounds(left, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Customization"), button -> switchTab(Tab.CUSTOMIZATION))
                .bounds(left + 126, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Debug"), button -> switchTab(Tab.DEBUG))
                .bounds(left + width - buttonWidth, top, buttonWidth, 20).build());
    }

    private void initSummoningTab(int left, int top) {
        limitSlider = register(addRenderableWidget(new LimitSlider(left, top + 18, 180, 20, snapshot.maxSummonCount())), summoningWidgets);

        limitBox = register(addRenderableWidget(new EditBox(font, left + 188, top + 18, 56, 20, Component.literal("Limit"))), summoningWidgets);
        limitBox.setValue(Integer.toString(snapshot.maxSummonCount()));
        register(addRenderableWidget(Button.builder(Component.literal("Apply Limit"), button -> applyLimit())
                .bounds(left + 248, top + 18, 98, 20).build()), summoningWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Recommended"), button -> {
            int recommended = recommendedBotAmount();
            limitBox.setValue(Integer.toString(recommended));
            countBox.setValue(Integer.toString(Math.min(recommended, parseInt(countBox.getValue(), 1))));
        }).bounds(left, top + 44, 116, 20).build()), summoningWidgets);

        perTickBox = register(addRenderableWidget(new EditBox(font, left + 124, top + 44, 56, 20, Component.literal("Rate"))), summoningWidgets);
        perTickBox.setValue(Integer.toString(snapshot.maxSpawnsPerTick()));
        register(addRenderableWidget(Button.builder(Component.literal("Apply Tick Rate"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_SPAWNS_PER_TICK,
                "",
                "",
                parseInt(perTickBox.getValue(), snapshot.maxSpawnsPerTick()),
                false
        ))).bounds(left + 186, top + 44, 112, 20).build()), summoningWidgets);

        countBox = register(addRenderableWidget(new EditBox(font, left, top + 88, 70, 20, Component.literal("Count"))), summoningWidgets);
        countBox.setValue("8");
        namesBox = register(addRenderableWidget(new EditBox(font, left + 76, top + 88, 270, 20, Component.literal("Usernames"))), summoningWidgets);
        namesBox.setHint(Component.literal("{Alpha, Bravo, Charlie}"));

        formationButton = register(addRenderableWidget(Button.builder(formationLabel(), button -> cycleFormation())
                .bounds(left, top + 114, 120, 20).build()), summoningWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Summon Batch"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SUMMON,
                namesBox.getValue(),
                "",
                parseInt(countBox.getValue(), 1),
                false
        ))).bounds(left + 128, top + 114, 110, 20).build()), summoningWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Get Wand"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.GIVE_WAND, "", "", 0, false
        ))).bounds(left + 244, top + 114, 102, 20).build()), summoningWidgets);
    }

    private void initCustomizationTab(int left, int top) {
        register(addRenderableWidget(Button.builder(Component.literal("Select All"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_ALL, "", "", 0, false
        ))).bounds(left, top + 18, 92, 20).build()), customizationWidgets);

        rangeBox = register(addRenderableWidget(new EditBox(font, left + 98, top + 18, 48, 20, Component.literal("Range"))), customizationWidgets);
        rangeBox.setValue("16");
        register(addRenderableWidget(Button.builder(Component.literal("Nearest X"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_RANGE, "", "", parseInt(rangeBox.getValue(), 16), false
        ))).bounds(left + 152, top + 18, 90, 20).build()), customizationWidgets);

        closestBox = register(addRenderableWidget(new EditBox(font, left + 248, top + 18, 48, 20, Component.literal("Count"))), customizationWidgets);
        closestBox.setValue("10");
        register(addRenderableWidget(Button.builder(Component.literal("Closest"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_CLOSEST, "", "", parseInt(closestBox.getValue(), 10), false
        ))).bounds(left + 302, top + 18, 84, 20).build()), customizationWidgets);

        register(addRenderableWidget(Button.builder(Component.literal("Clear Selection"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.CLEAR_SELECTION, "", "", 0, false
        ))).bounds(left, top + 44, 120, 20).build()), customizationWidgets);

        groupBox = register(addRenderableWidget(new EditBox(font, left, top + 88, 120, 20, Component.literal("Group"))), customizationWidgets);
        groupBox.setValue("alpha");
        register(addRenderableWidget(Button.builder(Component.literal("Create"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.CREATE_GROUP, groupBox.getValue(), "", 0, false
        ))).bounds(left + 126, top + 88, 70, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Assign"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.ASSIGN_GROUP, groupBox.getValue(), "", 0, false
        ))).bounds(left + 202, top + 88, 70, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Remove"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.REMOVE_GROUP, groupBox.getValue(), "", 0, false
        ))).bounds(left + 278, top + 88, 76, 20).build()), customizationWidgets);

        aiModeBox = register(addRenderableWidget(new EditBox(font, left, top + 132, 120, 20, Component.literal("AI Mode"))), customizationWidgets);
        aiModeBox.setValue(BotAiMode.IDLE.displayName());
        register(addRenderableWidget(Button.builder(Component.literal("Set Selected AI"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_SELECTED_AI, aiModeBox.getValue(), "", 0, false
        ))).bounds(left + 126, top + 132, 112, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Set Group AI"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_GROUP_AI, groupBox.getValue(), aiModeBox.getValue(), 0, false
        ))).bounds(left + 244, top + 132, 110, 20).build()), customizationWidgets);

        actionBox = register(addRenderableWidget(new EditBox(font, left, top + 176, 180, 20, Component.literal("Action"))), customizationWidgets);
        actionBox.setValue("attack once");
        register(addRenderableWidget(Button.builder(Component.literal("Run Action"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.RUN_ACTION, actionBox.getValue(), "", 0, false
        ))).bounds(left + 186, top + 176, 92, 20).build()), customizationWidgets);

        directionBox = register(addRenderableWidget(new EditBox(font, left, top + 202, 84, 20, Component.literal("Direction"))), customizationWidgets);
        directionBox.setValue("up");
        blockBox = register(addRenderableWidget(new EditBox(font, left + 90, top + 202, 100, 20, Component.literal("Block"))), customizationWidgets);
        blockBox.setValue("stone");
        register(addRenderableWidget(Button.builder(Component.literal("Teleport Selected"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.TELEPORT_SELECTION, directionBox.getValue(), blockBox.getValue(), 0, false
        ))).bounds(left + 196, top + 202, 126, 20).build()), customizationWidgets);
    }

    private void initDebugTab(int left, int top) {
        debugButton = register(addRenderableWidget(Button.builder(debugLabel(), button -> {
            boolean newDebug = !snapshot.debugEnabled();
            send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.SET_DEBUG, "", "", 0, newDebug));
        }).bounds(left, top + 18, 120, 20).build()), debugWidgets);

        register(addRenderableWidget(Button.builder(Component.literal("Refresh State"), button ->
                send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.REQUEST_STATE, "", "", 0, false)))
                .bounds(left + 126, top + 18, 110, 20).build()), debugWidgets);
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
        if (formationButton != null) {
            formationButton.setMessage(formationLabel());
        }
    }

    @Override
    public void onClose() {
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xE3111A21, 0xF0212D3A);
        guiGraphics.fill(width / 2 - 194, 36, width / 2 + 194, height - 18, 0x9A0D1318);
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        guiGraphics.drawCenteredString(font, activeTab.subtitle(), width / 2, 30, 0xBFD7E6);

        renderSummoningText(guiGraphics, width / 2 - 185, 42);
        renderCustomizationText(guiGraphics, width / 2 - 185, 42);
        renderDebugText(guiGraphics, width / 2 - 185, 42);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSummoningText(GuiGraphics guiGraphics, int left, int top) {
        if (activeTab != Tab.SUMMONING) {
            return;
        }

        guiGraphics.drawString(font, "Tab 1: Summoning", left, top, 0xEBDCA9);
        guiGraphics.drawString(font, "Bot count, username flow, performance limits, and live summon queue.", left, top + 68, 0xC3CED7);
        guiGraphics.drawString(font, "Formation preview: " + currentFormation(), left, top + 140, 0xA8E8D2);
        guiGraphics.drawString(font, progressText(), left, top + 156, 0xFFFFFF);
        guiGraphics.drawString(font, warningText(), left, top + 172, warningColor(), false);
        guiGraphics.drawString(font, "Loadouts, percent distributions, presets, and scenario save/load are next backend slices.", left, top + 196, 0xE8C89C);
        guiGraphics.drawString(font, "Spawned PlayerBatch bots are automatically tagged with 'bot'.", left, top + 212, 0x9BE5B8);
    }

    private void renderCustomizationText(GuiGraphics guiGraphics, int left, int top) {
        if (activeTab != Tab.CUSTOMIZATION) {
            return;
        }

        guiGraphics.drawString(font, "Tab 2: Customization", left, top, 0xEBDCA9);
        guiGraphics.drawString(font, "Selection, groups, AI mode assignment, actions, and teleport controls.", left, top + 68, 0xC3CED7);
        guiGraphics.drawString(font, "Supported AI modes: idle, combat, patrol, guard, follow, flee", left, top + 156, 0x9BE5B8);
        guiGraphics.drawString(font, "Groups: " + (snapshot.groups().isEmpty() ? "none yet" : String.join(" | ", snapshot.groups())), left, top + 238, 0xA8E8D2);
        guiGraphics.drawString(font, "Selected bots (" + snapshot.selectedNames().size() + "): " + selectedSummary(), left, top + 254, 0xBFD7E6);
        guiGraphics.drawString(font, "Armor/effects/loadout editors and wand area selection are still pending backend implementation.", left, top + 270, 0xE8C89C);
    }

    private void renderDebugText(GuiGraphics guiGraphics, int left, int top) {
        if (activeTab != Tab.DEBUG) {
            return;
        }

        guiGraphics.drawString(font, "Tab 3: Debug", left, top, 0xEBDCA9);
        guiGraphics.drawString(font, "Internal state, live counts, queue activity, and diagnostics toggles.", left, top + 46, 0xC3CED7);
        guiGraphics.drawString(font, "Debug enabled: " + snapshot.debugEnabled(), left, top + 84, 0xFFFFFF);
        guiGraphics.drawString(font, "Queued batches: " + snapshot.queuedBatches(), left, top + 100, 0xFFFFFF);
        guiGraphics.drawString(font, "Batch active: " + snapshot.batchActive(), left, top + 116, 0xFFFFFF);
        guiGraphics.drawString(font, "Selected bot count: " + snapshot.selectedNames().size(), left, top + 132, 0xFFFFFF);
        guiGraphics.drawString(font, "Managed groups: " + snapshot.groups().size(), left, top + 148, 0xFFFFFF);
        guiGraphics.drawString(font, "Success / fail / pending: " + snapshot.successCount() + " / " + snapshot.failCount() + " / " + snapshot.pendingCount(), left, top + 164, 0xFFFFFF);
        guiGraphics.drawString(font, "This debug tab is the staging point for future visual debug overlays and path/target tracing.", left, top + 196, 0xE8C89C);
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        refreshTabVisibility();
    }

    private void refreshTabVisibility() {
        setTabVisible(summoningWidgets, activeTab == Tab.SUMMONING);
        setTabVisible(customizationWidgets, activeTab == Tab.CUSTOMIZATION);
        setTabVisible(debugWidgets, activeTab == Tab.DEBUG);
    }

    private void setTabVisible(List<AbstractWidget> widgets, boolean visible) {
        for (AbstractWidget widget : widgets) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private void applyLimit() {
        int value = parseInt(limitBox.getValue(), snapshot.maxSummonCount());
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.SET_LIMIT, "", "", value, false));
    }

    private int recommendedBotAmount() {
        long maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        int recommended = (int) Math.max(24L, (maxMemoryMb - 768L) / 6L);
        return Math.max(1, Math.min(snapshot.maxSummonCount(), recommended));
    }

    private Component debugLabel() {
        return Component.literal("Debug: " + (snapshot.debugEnabled() ? "ON" : "OFF"));
    }

    private Component formationLabel() {
        return Component.literal("Formation: " + currentFormation());
    }

    private void cycleFormation() {
        formationIndex = (formationIndex + 1) % FORMATION_OPTIONS.size();
        if (formationButton != null) {
            formationButton.setMessage(formationLabel());
        }
    }

    private String currentFormation() {
        return FORMATION_OPTIONS.get(Math.max(0, Math.min(formationIndex, FORMATION_OPTIONS.size() - 1)));
    }

    private String progressText() {
        if (!snapshot.batchActive()) {
            return "Queue idle. Queued batches: " + snapshot.queuedBatches();
        }
        return "Summoning " + snapshot.successCount() + "/" + snapshot.totalCount()
                + " success, " + snapshot.failCount() + " failed, " + snapshot.pendingCount() + " pending";
    }

    private String warningText() {
        int count = parseInt(countBox == null ? "1" : countBox.getValue(), 1);
        if (count > 256) {
            return "Warning: batches above 256 bots may heavily impact TPS and entity processing.";
        }
        if (count > recommendedBotAmount()) {
            return "Caution: this requested count is above the local recommended amount.";
        }
        return "Within the current recommended range for this client profile.";
    }

    private int warningColor() {
        int count = parseInt(countBox == null ? "1" : countBox.getValue(), 1);
        if (count > 256) {
            return 0xFF9B8D;
        }
        if (count > recommendedBotAmount()) {
            return 0xF3D483;
        }
        return 0x9BE5B8;
    }

    private String selectedSummary() {
        if (snapshot.selectedNames().isEmpty()) {
            return "none";
        }
        int displayCount = Math.min(6, snapshot.selectedNames().size());
        List<String> names = snapshot.selectedNames().subList(0, displayCount);
        String summary = String.join(", ", names);
        if (snapshot.selectedNames().size() > displayCount) {
            summary += " +" + (snapshot.selectedNames().size() - displayCount) + " more";
        }
        return summary;
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

    private <T extends AbstractWidget> T register(T widget, List<AbstractWidget> widgets) {
        widgets.add(widget);
        return widget;
    }

    private enum Tab {
        SUMMONING("Batch spawn orchestration"),
        CUSTOMIZATION("Selection, groups, actions, and AI"),
        DEBUG("Diagnostics and internal state");

        private final String subtitle;

        Tab(String subtitle) {
            this.subtitle = subtitle;
        }

        public String subtitle() {
            return subtitle;
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
            setMessage(Component.literal("Max Bots: " + currentLimit));
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
