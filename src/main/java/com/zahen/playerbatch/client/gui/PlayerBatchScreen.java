package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.client.PlayerBatchUiPreferences;
import com.zahen.playerbatch.core.BotConfig;
import com.zahen.playerbatch.core.BotAiMode;
import com.zahen.playerbatch.core.BotLoadout;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class PlayerBatchScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> summoningWidgets = new ArrayList<>();
    private final List<AbstractWidget> summoningSetupWidgets = new ArrayList<>();
    private final List<AbstractWidget> summoningLoadoutWidgets = new ArrayList<>();
    private final List<AbstractWidget> customizationWidgets = new ArrayList<>();
    private final List<AbstractWidget> debugWidgets = new ArrayList<>();
    private static final List<String> FORMATION_OPTIONS = List.of("circle", "square", "triangle", "random", "single block");
    private static final List<String> ACTION_OPTIONS = List.of(
            "attack once",
            "attack continuous",
            "jump once",
            "jump continuous",
            "use once",
            "use continuous",
            "drop all",
            "drop stack",
            "swapHands",
            "stop",
            "sneak",
            "unsneak",
            "sprint",
            "unsprint",
            "turn left",
            "turn right",
            "look north",
            "look south",
            "look east",
            "look west",
            "look up",
            "look down"
    );
    private static final List<String> SLOT_OPTIONS = List.of("head", "chest", "legs", "feet", "mainhand", "offhand");
    private static final List<String> DIRECTION_OPTIONS = List.of("up", "below", "north", "south", "east", "west");
    private static final List<String> ITEM_OPTIONS = BuiltInRegistries.ITEM.keySet().stream()
            .map(Object::toString)
            .sorted()
            .toList();
    private static final List<String> EFFECT_OPTIONS = BuiltInRegistries.MOB_EFFECT.keySet().stream()
            .map(Object::toString)
            .sorted()
            .toList();
    private static final List<String> BLOCK_OPTIONS = BuiltInRegistries.BLOCK.keySet().stream()
            .map(Object::toString)
            .sorted()
            .toList();
    private static final List<String> AI_MODE_OPTIONS = List.of(BotAiMode.values()).stream()
            .map(BotAiMode::displayName)
            .sorted()
            .toList();

    private Tab activeTab = Tab.SUMMONING;
    private SummonPane activeSummonPane = SummonPane.SETUP;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;
    private final PlayerBatchUiPreferences preferences;

    private LimitSlider limitSlider;
    private EditBox countBox;
    private EditBox namesBox;
    private EditBox limitBox;
    private EditBox perTickBox;
    private Button formationButton;
    private int formationIndex = 0;
    private EditBox summonHeadBox;
    private EditBox summonChestBox;
    private EditBox summonLegsBox;
    private EditBox summonFeetBox;
    private EditBox summonMainhandBox;
    private EditBox summonOffhandBox;
    private EditBox summonHotbarBox;
    private EditBox summonEffectBox;
    private EditBox summonEffectDurationBox;
    private EditBox summonEffectAmplifierBox;

    private EditBox rangeBox;
    private EditBox closestBox;
    private EditBox groupBox;
    private EditBox aiModeBox;
    private EditBox slotBox;
    private EditBox itemBox;
    private EditBox itemCountBox;
    private EditBox effectBox;
    private EditBox effectDurationBox;
    private EditBox effectAmplifierBox;
    private EditBox actionBox;
    private EditBox directionBox;
    private EditBox blockBox;

    private Button debugButton;
    private boolean applyingAutocomplete;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.snapshot = PlayerBatchClient.latestSnapshot();
        this.preferences = PlayerBatchUiPreferences.load();
        this.activeTab = parseTab(preferences.activeTab());
        this.activeSummonPane = parseSummonPane(preferences.summonPane());
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
        initSummonPaneButtons(left, top);

        limitSlider = register(addRenderableWidget(new LimitSlider(left, top + 18, 180, 20, snapshot.maxSummonCount())), summoningSetupWidgets);

        limitBox = register(addRenderableWidget(new EditBox(font, left + 188, top + 18, 56, 20, Component.literal("Limit"))), summoningSetupWidgets);
        limitBox.setValue(Integer.toString(snapshot.maxSummonCount()));
        register(addRenderableWidget(Button.builder(Component.literal("Apply Limit"), button -> applyLimit())
                .bounds(left + 248, top + 18, 98, 20).build()), summoningSetupWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Recommended"), button -> {
            int recommended = recommendedBotAmount();
            limitBox.setValue(Integer.toString(recommended));
            countBox.setValue(Integer.toString(Math.min(recommended, parseInt(countBox.getValue(), 1))));
            savePreferences();
        }).bounds(left, top + 44, 116, 20).build()), summoningSetupWidgets);

        perTickBox = register(addRenderableWidget(new EditBox(font, left + 124, top + 44, 56, 20, Component.literal("Rate"))), summoningSetupWidgets);
        perTickBox.setValue(Integer.toString(snapshot.maxSpawnsPerTick()));
        register(addRenderableWidget(Button.builder(Component.literal("Apply Tick Rate"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_SPAWNS_PER_TICK,
                "",
                "",
                parseInt(perTickBox.getValue(), snapshot.maxSpawnsPerTick()),
                false
        ))).bounds(left + 186, top + 44, 112, 20).build()), summoningSetupWidgets);

        countBox = register(addRenderableWidget(new EditBox(font, left, top + 88, 70, 20, Component.literal("Count"))), summoningSetupWidgets);
        countBox.setValue(preferences.summonCount());
        countBox.setResponder(value -> savePreferences());
        namesBox = register(addRenderableWidget(new EditBox(font, left + 76, top + 88, 270, 20, Component.literal("Usernames"))), summoningSetupWidgets);
        namesBox.setValue(preferences.summonNames());
        namesBox.setHint(Component.literal("{Alpha, Bravo, Charlie}"));
        namesBox.setResponder(value -> savePreferences());

        formationIndex = formationIndex(preferences.summonFormation());
        formationButton = register(addRenderableWidget(Button.builder(formationLabel(), button -> cycleFormation())
                .bounds(left, top + 114, 120, 20).build()), summoningSetupWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Summon Batch"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SUMMON,
                namesBox.getValue(),
                buildSummonConfig().encode(),
                parseInt(countBox.getValue(), 1),
                false
        ))).bounds(left + 128, top + 114, 110, 20).build()), summoningSetupWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Get Wand"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.GIVE_WAND, "", "", 0, false
        ))).bounds(left + 244, top + 114, 102, 20).build()), summoningSetupWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Select All Bots"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_ALL, "", "", 0, false
        ))).bounds(left, top + 140, 140, 20).build()), summoningSetupWidgets);

        summonHeadBox = register(addRenderableWidget(new EditBox(font, left, top + 52, 116, 20, Component.literal("Helmet"))), summoningLoadoutWidgets);
        summonChestBox = register(addRenderableWidget(new EditBox(font, left + 126, top + 52, 116, 20, Component.literal("Chestplate"))), summoningLoadoutWidgets);
        summonLegsBox = register(addRenderableWidget(new EditBox(font, left + 252, top + 52, 116, 20, Component.literal("Leggings"))), summoningLoadoutWidgets);
        summonFeetBox = register(addRenderableWidget(new EditBox(font, left, top + 82, 116, 20, Component.literal("Boots"))), summoningLoadoutWidgets);
        summonMainhandBox = register(addRenderableWidget(new EditBox(font, left + 126, top + 82, 116, 20, Component.literal("Main Hand"))), summoningLoadoutWidgets);
        summonOffhandBox = register(addRenderableWidget(new EditBox(font, left + 252, top + 82, 116, 20, Component.literal("Offhand"))), summoningLoadoutWidgets);
        summonHotbarBox = register(addRenderableWidget(new EditBox(font, left, top + 118, 368, 20, Component.literal("Hotbar"))), summoningLoadoutWidgets);
        summonHotbarBox.setHint(Component.literal("slot1,slot2,slot3..."));
        summonEffectBox = register(addRenderableWidget(new EditBox(font, left, top + 160, 140, 20, Component.literal("Effect"))), summoningLoadoutWidgets);
        summonEffectDurationBox = register(addRenderableWidget(new EditBox(font, left + 148, top + 160, 64, 20, Component.literal("Secs"))), summoningLoadoutWidgets);
        summonEffectAmplifierBox = register(addRenderableWidget(new EditBox(font, left + 220, top + 160, 64, 20, Component.literal("Amp"))), summoningLoadoutWidgets);
        initSummonLoadoutFields();
    }

    private void initCustomizationTab(int left, int top) {
        register(addRenderableWidget(Button.builder(Component.literal("Select All Bots"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_ALL, "", "", 0, false
        ))).bounds(left, top + 18, 120, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Deselect All"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.CLEAR_SELECTION, "", "", 0, false
        ))).bounds(left + 126, top + 18, 108, 20).build()), customizationWidgets);

        rangeBox = register(addRenderableWidget(new EditBox(font, left + 240, top + 18, 40, 20, Component.literal("Range"))), customizationWidgets);
        rangeBox.setValue(preferences.selectRange());
        rangeBox.setResponder(value -> savePreferences());
        register(addRenderableWidget(Button.builder(Component.literal("Nearest X"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_RANGE, "", "", parseInt(rangeBox.getValue(), 16), false
        ))).bounds(left + 286, top + 18, 82, 20).build()), customizationWidgets);

        closestBox = register(addRenderableWidget(new EditBox(font, left + 240, top + 44, 40, 20, Component.literal("Count"))), customizationWidgets);
        closestBox.setValue(preferences.selectClosest());
        closestBox.setResponder(value -> savePreferences());
        register(addRenderableWidget(Button.builder(Component.literal("Closest"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SELECT_CLOSEST, "", "", parseInt(closestBox.getValue(), 10), false
        ))).bounds(left + 286, top + 44, 82, 20).build()), customizationWidgets);

        register(addRenderableWidget(Button.builder(Component.literal("Complete Focus"), button -> completeFocusedAutocomplete())
                .bounds(left, top + 44, 120, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Action Editor"), button -> Minecraft.getInstance().setScreen(new ActionEditorScreen(this)))
                .bounds(left, top + 70, 120, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Loadout Editor"), button -> Minecraft.getInstance().setScreen(new LoadoutEditorScreen(this)))
                .bounds(left + 126, top + 70, 120, 20).build()), customizationWidgets);

        groupBox = register(addRenderableWidget(new EditBox(font, left, top + 88, 120, 20, Component.literal("Group"))), customizationWidgets);
        groupBox.setValue(preferences.groupName());
        groupBox.setResponder(value -> {
            updateAutocomplete(groupBox, currentGroupOptions());
            savePreferences();
        });
        updateAutocomplete(groupBox, currentGroupOptions());
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
        aiModeBox.setValue(preferences.aiMode());
        aiModeBox.setResponder(value -> {
            updateAutocomplete(aiModeBox, AI_MODE_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(aiModeBox, AI_MODE_OPTIONS);
        register(addRenderableWidget(Button.builder(Component.literal("Set Selected AI"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_SELECTED_AI, aiModeBox.getValue(), "", 0, false
        ))).bounds(left + 126, top + 132, 112, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Set Group AI"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SET_GROUP_AI, groupBox.getValue(), aiModeBox.getValue(), 0, false
        ))).bounds(left + 244, top + 132, 110, 20).build()), customizationWidgets);

        slotBox = register(addRenderableWidget(new EditBox(font, left, top + 176, 70, 20, Component.literal("Slot"))), customizationWidgets);
        slotBox.setValue(preferences.itemSlot());
        slotBox.setResponder(value -> {
            updateAutocomplete(slotBox, SLOT_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(slotBox, SLOT_OPTIONS);
        itemBox = register(addRenderableWidget(new EditBox(font, left + 76, top + 176, 120, 20, Component.literal("Item"))), customizationWidgets);
        itemBox.setValue(preferences.itemId());
        itemBox.setResponder(value -> {
            updateAutocomplete(itemBox, ITEM_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(itemBox, ITEM_OPTIONS);
        itemCountBox = register(addRenderableWidget(new EditBox(font, left + 202, top + 176, 40, 20, Component.literal("Count"))), customizationWidgets);
        itemCountBox.setValue(preferences.itemCount());
        itemCountBox.setResponder(value -> savePreferences());
        register(addRenderableWidget(Button.builder(Component.literal("Apply Item"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.APPLY_SELECTED_ITEM, slotBox.getValue(), itemBox.getValue(), parseInt(itemCountBox.getValue(), 1), false
        ))).bounds(left + 248, top + 176, 106, 20).build()), customizationWidgets);

        effectBox = register(addRenderableWidget(new EditBox(font, left, top + 202, 120, 20, Component.literal("Effect"))), customizationWidgets);
        effectBox.setValue(preferences.effectId());
        effectBox.setResponder(value -> {
            updateAutocomplete(effectBox, EFFECT_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(effectBox, EFFECT_OPTIONS);
        effectDurationBox = register(addRenderableWidget(new EditBox(font, left + 126, top + 202, 54, 20, Component.literal("Secs"))), customizationWidgets);
        effectDurationBox.setValue(preferences.effectDuration());
        effectDurationBox.setResponder(value -> savePreferences());
        effectAmplifierBox = register(addRenderableWidget(new EditBox(font, left + 186, top + 202, 54, 20, Component.literal("Amp"))), customizationWidgets);
        effectAmplifierBox.setValue(preferences.effectAmplifier());
        effectAmplifierBox.setResponder(value -> savePreferences());
        register(addRenderableWidget(Button.builder(Component.literal("Apply Effect"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.APPLY_SELECTED_EFFECT, effectBox.getValue(), effectAmplifierBox.getValue(), parseInt(effectDurationBox.getValue(), 30), false
        ))).bounds(left + 246, top + 202, 108, 20).build()), customizationWidgets);

        actionBox = register(addRenderableWidget(new EditBox(font, left, top + 228, 180, 20, Component.literal("Action"))), customizationWidgets);
        actionBox.setValue(preferences.action());
        actionBox.setResponder(value -> {
            updateAutocomplete(actionBox, ACTION_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(actionBox, ACTION_OPTIONS);
        register(addRenderableWidget(Button.builder(Component.literal("Run Action"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.RUN_ACTION, actionBox.getValue(), "", 0, false
        ))).bounds(left + 284, top + 228, 102, 20).build()), customizationWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Clear Effects"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.CLEAR_SELECTED_EFFECTS, "", "", 0, false
        ))).bounds(left + 244, top + 254, 112, 20).build()), customizationWidgets);

        directionBox = register(addRenderableWidget(new EditBox(font, left, top + 254, 84, 20, Component.literal("Direction"))), customizationWidgets);
        directionBox.setValue(preferences.direction());
        directionBox.setResponder(value -> {
            updateAutocomplete(directionBox, DIRECTION_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(directionBox, DIRECTION_OPTIONS);
        blockBox = register(addRenderableWidget(new EditBox(font, left + 90, top + 254, 100, 20, Component.literal("Block"))), customizationWidgets);
        blockBox.setValue(preferences.block());
        blockBox.setResponder(value -> {
            updateAutocomplete(blockBox, BLOCK_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(blockBox, BLOCK_OPTIONS);
        register(addRenderableWidget(Button.builder(Component.literal("Teleport Selected"), button -> send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.TELEPORT_SELECTION, directionBox.getValue(), blockBox.getValue(), 0, false
        ))).bounds(left + 196, top + 280, 126, 20).build()), customizationWidgets);
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
        refreshAutocompleteSuggestions();
    }

    @Override
    public void onClose() {
        savePreferences();
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
        if (activeSummonPane == SummonPane.SETUP) {
            guiGraphics.drawString(font, "Setup pane: count, usernames, formation, and batch limits before spawn.", left, top + 68, 0xC3CED7);
            guiGraphics.drawString(font, "Formation preview: " + currentFormation(), left, top + 140, 0xA8E8D2);
            guiGraphics.drawString(font, "Select All Bots ignores distance and grabs every managed PlayerBatch bot.", left, top + 156, 0x9BE5B8);
            guiGraphics.drawString(font, progressText(), left, top + 172, 0xFFFFFF);
            guiGraphics.drawString(font, warningText(), left, top + 188, warningColor(), false);
            guiGraphics.drawString(font, "Summon now carries a prebuilt BotConfig instead of just post-spawn edits.", left, top + 212, 0xE8C89C);
            guiGraphics.drawString(font, "Spawned PlayerBatch bots are automatically tagged with 'bot'.", left, top + 228, 0x9BE5B8);
            return;
        }
        if (activeSummonPane == SummonPane.LOADOUT) {
            guiGraphics.drawString(font, "Loadout pane: armor, hands, hotbar row, and summon-time effects.", left, top + 68, 0xC3CED7);
            guiGraphics.drawString(font, "These settings are packed into BotConfig and applied as each bot resolves.", left, top + 84, 0x9BE5B8);
            guiGraphics.drawString(font, "Hotbar format: comma-separated items for slots 1-9.", left, top + 100, 0x9BE5B8);
            guiGraphics.drawString(font, "Example: stone_sword,bow,bread", left, top + 116, 0xA8E8D2);
            return;
        }
        guiGraphics.drawString(font, "Distribution pane", left, top + 68, 0xC3CED7);
        guiGraphics.drawString(font, "BotConfig and summon-time loadouts are live now; percent split rules are the next slice.", left, top + 84, 0xE8C89C);
        guiGraphics.drawString(font, "This pane is where preset-driven 50/30/20-style loadout spreads will land.", left, top + 100, 0xE8C89C);
    }

    private void renderCustomizationText(GuiGraphics guiGraphics, int left, int top) {
        if (activeTab != Tab.CUSTOMIZATION) {
            return;
        }

        guiGraphics.drawString(font, "Tab 2: Customization", left, top, 0xEBDCA9);
        guiGraphics.drawString(font, "Selection, groups, AI mode assignment, item/armor editing, effects, and teleport controls.", left, top + 68, 0xC3CED7);
        guiGraphics.drawString(font, "Select All Bots is global and does not use distance limits.", left, top + 84, 0x9BE5B8);
        guiGraphics.drawString(font, "Action Editor builds multi-action sets. Loadout Editor gives slot-based armor/hotbar editing.", left, top + 100, 0x9BE5B8);
        guiGraphics.drawString(font, "Supported AI modes: idle, combat, patrol, guard, follow, flee", left, top + 156, 0x9BE5B8);
        guiGraphics.drawString(font, "AI combos work too: follow+combat, guard+combat, follow+guard", left, top + 172, 0x9BE5B8);
        guiGraphics.drawString(font, "Item slots: head, chest, legs, feet, mainhand, offhand", left, top + 188, 0x9BE5B8);
        guiGraphics.drawString(font, "Autocomplete works for group, AI, slot, item, effect, action, direction, and block fields.", left, top + 204, 0x9BE5B8);
        guiGraphics.drawString(font, "Use Complete Focus to accept the suggestion for the text box you're editing.", left, top + 220, 0x9BE5B8);
        guiGraphics.drawString(font, "Groups: " + (snapshot.groups().isEmpty() ? "none yet" : String.join(" | ", snapshot.groups())), left, top + 290, 0xA8E8D2);
        guiGraphics.drawString(font, "Selected bots (" + snapshot.selectedNames().size() + "): " + selectedSummary(), left, top + 306, 0xBFD7E6);
        guiGraphics.drawString(font, "Full inventory pages, enchant editing, and wand area selection are still pending backend implementation.", left, top + 322, 0xE8C89C);
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
        savePreferences();
        refreshTabVisibility();
    }

    private void refreshTabVisibility() {
        setTabVisible(summoningWidgets, activeTab == Tab.SUMMONING);
        setTabVisible(summoningSetupWidgets, activeTab == Tab.SUMMONING && activeSummonPane == SummonPane.SETUP);
        setTabVisible(summoningLoadoutWidgets, activeTab == Tab.SUMMONING && activeSummonPane == SummonPane.LOADOUT);
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

    private void initSummonPaneButtons(int left, int top) {
        register(addRenderableWidget(Button.builder(Component.literal("Setup"), button -> switchSummonPane(SummonPane.SETUP))
                .bounds(left, top - 10, 88, 20).build()), summoningWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Loadout"), button -> switchSummonPane(SummonPane.LOADOUT))
                .bounds(left + 94, top - 10, 88, 20).build()), summoningWidgets);
        register(addRenderableWidget(Button.builder(Component.literal("Distribution"), button -> switchSummonPane(SummonPane.DISTRIBUTION))
                .bounds(left + 188, top - 10, 104, 20).build()), summoningWidgets);
    }

    private void initSummonLoadoutFields() {
        bindSummonItemBox(summonHeadBox, preferences.summonHead());
        bindSummonItemBox(summonChestBox, preferences.summonChest());
        bindSummonItemBox(summonLegsBox, preferences.summonLegs());
        bindSummonItemBox(summonFeetBox, preferences.summonFeet());
        bindSummonItemBox(summonMainhandBox, preferences.summonMainhand());
        bindSummonItemBox(summonOffhandBox, preferences.summonOffhand());
        summonHotbarBox.setValue(preferences.summonHotbar());
        summonHotbarBox.setResponder(value -> savePreferences());
        summonEffectBox.setValue(preferences.summonEffectId());
        summonEffectBox.setResponder(value -> {
            updateAutocomplete(summonEffectBox, EFFECT_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(summonEffectBox, EFFECT_OPTIONS);
        summonEffectDurationBox.setValue(preferences.summonEffectDuration());
        summonEffectDurationBox.setResponder(value -> savePreferences());
        summonEffectAmplifierBox.setValue(preferences.summonEffectAmplifier());
        summonEffectAmplifierBox.setResponder(value -> savePreferences());
    }

    private void bindSummonItemBox(EditBox box, String value) {
        box.setValue(value);
        box.setResponder(raw -> {
            updateAutocomplete(box, ITEM_OPTIONS);
            savePreferences();
        });
        updateAutocomplete(box, ITEM_OPTIONS);
    }

    private void cycleFormation() {
        formationIndex = (formationIndex + 1) % FORMATION_OPTIONS.size();
        if (formationButton != null) {
            formationButton.setMessage(formationLabel());
        }
        savePreferences();
    }

    private void switchSummonPane(SummonPane pane) {
        activeSummonPane = pane;
        savePreferences();
        refreshTabVisibility();
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

    private void refreshAutocompleteSuggestions() {
        updateAutocomplete(summonHeadBox, ITEM_OPTIONS);
        updateAutocomplete(summonChestBox, ITEM_OPTIONS);
        updateAutocomplete(summonLegsBox, ITEM_OPTIONS);
        updateAutocomplete(summonFeetBox, ITEM_OPTIONS);
        updateAutocomplete(summonMainhandBox, ITEM_OPTIONS);
        updateAutocomplete(summonOffhandBox, ITEM_OPTIONS);
        updateAutocomplete(summonEffectBox, EFFECT_OPTIONS);
        updateAutocomplete(groupBox, currentGroupOptions());
        updateAutocomplete(aiModeBox, AI_MODE_OPTIONS);
        updateAutocomplete(slotBox, SLOT_OPTIONS);
        updateAutocomplete(itemBox, ITEM_OPTIONS);
        updateAutocomplete(effectBox, EFFECT_OPTIONS);
        updateAutocomplete(actionBox, ACTION_OPTIONS);
        updateAutocomplete(directionBox, DIRECTION_OPTIONS);
        updateAutocomplete(blockBox, BLOCK_OPTIONS);
    }

    private void updateAutocomplete(EditBox box, Collection<String> options) {
        if (box == null) {
            return;
        }
        String completion = autocompleteValue(box.getValue(), options);
        if (completion == null) {
            box.setSuggestion(null);
            return;
        }
        String currentValue = box.getValue();
        if (completion.equalsIgnoreCase(currentValue)) {
            box.setSuggestion(null);
            return;
        }
        box.setSuggestion(completion.substring(Math.min(currentValue.length(), completion.length())));
        if (!applyingAutocomplete && box.isFocused()) {
            String uniqueCompletion = uniqueAutocompleteValue(currentValue, options);
            if (uniqueCompletion != null && !uniqueCompletion.equalsIgnoreCase(currentValue)) {
                applyingAutocomplete = true;
                box.setValue(uniqueCompletion);
                box.moveCursorToEnd(false);
                applyingAutocomplete = false;
                box.setSuggestion(null);
            }
        }
    }

    private void completeFocusedAutocomplete() {
        completeBox(summonHeadBox, ITEM_OPTIONS);
        completeBox(summonChestBox, ITEM_OPTIONS);
        completeBox(summonLegsBox, ITEM_OPTIONS);
        completeBox(summonFeetBox, ITEM_OPTIONS);
        completeBox(summonMainhandBox, ITEM_OPTIONS);
        completeBox(summonOffhandBox, ITEM_OPTIONS);
        completeBox(summonEffectBox, EFFECT_OPTIONS);
        completeBox(groupBox, currentGroupOptions());
        completeBox(aiModeBox, AI_MODE_OPTIONS);
        completeBox(slotBox, SLOT_OPTIONS);
        completeBox(itemBox, ITEM_OPTIONS);
        completeBox(effectBox, EFFECT_OPTIONS);
        completeBox(actionBox, ACTION_OPTIONS);
        completeBox(directionBox, DIRECTION_OPTIONS);
        completeBox(blockBox, BLOCK_OPTIONS);
    }

    private void completeBox(EditBox box, Collection<String> options) {
        if (box == null || !box.isFocused()) {
            return;
        }
        String completion = autocompleteValue(box.getValue(), options);
        if (completion == null) {
            return;
        }
        box.setValue(completion);
        box.moveCursorToEnd(false);
        updateAutocomplete(box, options);
        savePreferences();
    }

    private String autocompleteValue(String currentValue, Collection<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        String normalized = currentValue == null ? "" : currentValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return options.iterator().next();
        }
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                return option;
            }
        }
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).contains(normalized)) {
                return option;
            }
        }
        return null;
    }

    private String uniqueAutocompleteValue(String currentValue, Collection<String> options) {
        String normalized = currentValue == null ? "" : currentValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return null;
        }
        String match = null;
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                if (match != null) {
                    return null;
                }
                match = option;
            }
        }
        return match;
    }

    private List<String> currentGroupOptions() {
        if (snapshot == null || snapshot.groups().isEmpty()) {
            return List.of();
        }
        List<String> groups = new ArrayList<>();
        for (String summary : snapshot.groups()) {
            int equalsIndex = summary.indexOf('=');
            groups.add((equalsIndex >= 0 ? summary.substring(0, equalsIndex) : summary).trim());
        }
        return groups;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int formationIndex(String formation) {
        int index = FORMATION_OPTIONS.indexOf(formation == null ? "" : formation.toLowerCase(Locale.ROOT));
        return index >= 0 ? index : 0;
    }

    private Tab parseTab(String rawTab) {
        try {
            return Tab.valueOf((rawTab == null ? Tab.SUMMONING.name() : rawTab).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Tab.SUMMONING;
        }
    }

    private SummonPane parseSummonPane(String rawPane) {
        try {
            return SummonPane.valueOf((rawPane == null ? SummonPane.SETUP.name() : rawPane).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SummonPane.SETUP;
        }
    }

    private void savePreferences() {
        preferences.setActiveTab(activeTab.name());
        preferences.setSummonCount(valueOf(countBox));
        preferences.setSummonNames(valueOf(namesBox));
        preferences.setSummonFormation(currentFormation());
        preferences.setSummonPane(activeSummonPane.name());
        preferences.setSummonHead(normalizeRegistryValue(valueOf(summonHeadBox)));
        preferences.setSummonChest(normalizeRegistryValue(valueOf(summonChestBox)));
        preferences.setSummonLegs(normalizeRegistryValue(valueOf(summonLegsBox)));
        preferences.setSummonFeet(normalizeRegistryValue(valueOf(summonFeetBox)));
        preferences.setSummonMainhand(normalizeRegistryValue(valueOf(summonMainhandBox)));
        preferences.setSummonOffhand(normalizeRegistryValue(valueOf(summonOffhandBox)));
        preferences.setSummonHotbar(valueOf(summonHotbarBox));
        preferences.setSummonEffectId(normalizeRegistryValue(valueOf(summonEffectBox)));
        preferences.setSummonEffectDuration(valueOf(summonEffectDurationBox));
        preferences.setSummonEffectAmplifier(valueOf(summonEffectAmplifierBox));
        preferences.setSelectRange(valueOf(rangeBox));
        preferences.setSelectClosest(valueOf(closestBox));
        preferences.setGroupName(valueOf(groupBox));
        preferences.setAiMode(valueOf(aiModeBox).toLowerCase(Locale.ROOT));
        preferences.setItemSlot(valueOf(slotBox).toLowerCase(Locale.ROOT));
        preferences.setItemId(valueOf(itemBox).toLowerCase(Locale.ROOT));
        preferences.setItemCount(valueOf(itemCountBox));
        preferences.setEffectId(valueOf(effectBox).toLowerCase(Locale.ROOT));
        preferences.setEffectDuration(valueOf(effectDurationBox));
        preferences.setEffectAmplifier(valueOf(effectAmplifierBox));
        preferences.setAction(valueOf(actionBox));
        preferences.setDirection(valueOf(directionBox).toLowerCase(Locale.ROOT));
        preferences.setBlock(valueOf(blockBox).toLowerCase(Locale.ROOT));
        preferences.save();
    }

    private BotConfig buildSummonConfig() {
        BotLoadout loadout = new BotLoadout();
        putEquipment(loadout, EquipmentSlot.HEAD, summonHeadBox);
        putEquipment(loadout, EquipmentSlot.CHEST, summonChestBox);
        putEquipment(loadout, EquipmentSlot.LEGS, summonLegsBox);
        putEquipment(loadout, EquipmentSlot.FEET, summonFeetBox);
        putEquipment(loadout, EquipmentSlot.MAINHAND, summonMainhandBox);
        putEquipment(loadout, EquipmentSlot.OFFHAND, summonOffhandBox);
        String[] hotbarEntries = valueOf(summonHotbarBox).split(",");
        for (int index = 0; index < hotbarEntries.length && index < 9; index++) {
            String itemId = normalizeRegistryValue(hotbarEntries[index]);
            if (!itemId.isEmpty()) {
                loadout.hotbar().put(index, new BotLoadout.StackSpec(itemId, 1));
            }
        }
        String effectId = normalizeRegistryValue(valueOf(summonEffectBox));
        if (!effectId.isEmpty()) {
            loadout.effects().add(new BotLoadout.EffectSpec(
                    effectId,
                    parseInt(valueOf(summonEffectDurationBox), 30),
                    parseInt(valueOf(summonEffectAmplifierBox), 0)
            ));
        }
        return new BotConfig(currentFormation(), loadout);
    }

    private void putEquipment(BotLoadout loadout, EquipmentSlot slot, EditBox box) {
        String itemId = normalizeRegistryValue(valueOf(box));
        if (!itemId.isEmpty()) {
            loadout.equipment().put(slot, new BotLoadout.StackSpec(itemId, 1));
        }
    }

    private String normalizeRegistryValue(String raw) {
        String trimmed = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private String valueOf(EditBox box) {
        return box == null ? "" : box.getValue();
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

    private enum SummonPane {
        SETUP,
        LOADOUT,
        DISTRIBUTION
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
