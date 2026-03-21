package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.client.PlayerBatchUiPreferences;
import com.zahen.playerbatch.core.BotConfig;
import com.zahen.playerbatch.core.BotLoadout;
import com.zahen.playerbatch.core.CombatPresetSpec;
import com.zahen.playerbatch.core.PlayerBatchService;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.Locale;

public final class PlayerBatchScreen extends Screen {
    private static final List<String> ARMOR_MATERIALS = List.of("none", "leather", "chainmail", "iron", "golden", "diamond", "netherite");
    private static final List<String> TOOL_MATERIALS = List.of("none", "wooden", "stone", "iron", "diamond", "netherite");
    private static final List<String> FORMATIONS = List.of("circle", "square", "triangle", "random", "single block");

    private final Screen parent;
    private final PlayerBatchUiPreferences preferences;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    private WizardStep step;
    private EditBox countBox;
    private EditBox namesBox;

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.preferences = PlayerBatchUiPreferences.load();
        this.snapshot = PlayerBatchClient.latestSnapshot();
        this.step = WizardStep.BASICS;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = panelLeft();
        int top = panelTop();
        int bottom = panelBottom();

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left, bottom - 24, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(step == WizardStep.FORMATION ? "Summon Bots" : "Continue"), button -> advance())
                .bounds(panelRight() - 110, bottom - 24, 100, 20).build());
        if (step != WizardStep.BASICS) {
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> goBack())
                    .bounds(panelRight() - 196, bottom - 24, 80, 20).build());
        }

        switch (step) {
            case BASICS -> initBasicsStep(left, top);
            case LOADOUT -> initLoadoutStep(left, top);
            case ARGUMENTS -> initArgumentsStep(left, top);
            case FORMATION -> initFormationStep(left, top);
        }
        requestState(false);
    }

    private void initBasicsStep(int left, int top) {
        countBox = addBox(left + 18, top + 112, 88, preferences.summonCount(), this::saveBasicsDraft);
        namesBox = addBox(left + 120, top + 112, panelWidth() - 156, preferences.summonNames(), this::saveBasicsDraft);
        namesBox.setMaxLength(32767);
        countBox.setMaxLength(32767);
    }

    private void initLoadoutStep(int left, int top) {
        int buttonWidth = 164;
        int gap = 18;
        addRenderableWidget(Button.builder(Component.literal("Armor: " + preferences.summonArmorMaterial()), button -> {
            preferences.setSummonArmorMaterial(cycle(ARMOR_MATERIALS, preferences.summonArmorMaterial()));
            preferences.save();
            init();
        }).bounds(left + 18, top + 120, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Tools: " + preferences.summonToolMaterial()), button -> {
            preferences.setSummonToolMaterial(cycle(TOOL_MATERIALS, preferences.summonToolMaterial()));
            preferences.save();
            init();
        }).bounds(left + 18 + buttonWidth + gap, top + 120, buttonWidth, 20).build());
    }

    private void initArgumentsStep(int left, int top) {
        int buttonWidth = 168;
        int rowGap = 14;
        int columnGap = 18;
        int rowOneY = top + 112;
        int rowTwoY = rowOneY + 20 + rowGap;
        int rowThreeY = rowTwoY + 20 + rowGap;
        int rowFourY = rowThreeY + 20 + rowGap;

        addRenderableWidget(Button.builder(Component.literal("Reach: " + preferences.summonReach()), button -> {
            preferences.setSummonReach(nextReach(preferences.summonReach()));
            preferences.save();
            init();
        }).bounds(left + 18, rowOneY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Fake Hit", preferences.summonFakeHit())), button -> {
            preferences.setSummonFakeHit(toggle(preferences.summonFakeHit()));
            if (!parseBoolean(preferences.summonFakeHit(), false)) {
                preferences.setSummonDamage("false");
            }
            preferences.save();
            init();
        }).bounds(left + 18 + buttonWidth + columnGap, rowOneY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("STAP", preferences.summonStap())), button -> {
            preferences.setSummonStap(toggle(preferences.summonStap()));
            preferences.save();
            init();
        }).bounds(left + 18, rowTwoY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Damage", preferences.summonDamage())), button -> {
            preferences.setSummonDamage(toggle(preferences.summonDamage()));
            if (parseBoolean(preferences.summonDamage(), false)) {
                preferences.setSummonFakeHit("true");
            }
            preferences.save();
            init();
        }).bounds(left + 18 + buttonWidth + columnGap, rowTwoY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("360 Flex", preferences.summon360Flex())), button -> {
            preferences.setSummon360Flex(toggle(preferences.summon360Flex()));
            preferences.save();
            init();
        }).bounds(left + 18, rowThreeY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Self Heal", preferences.summonSelfHeal())), button -> {
            preferences.setSummonSelfHeal(toggle(preferences.summonSelfHeal()));
            if (!parseBoolean(preferences.summonSelfHeal(), false)) {
                preferences.setSummonHealingItems("false");
            }
            preferences.save();
            init();
        }).bounds(left + 18 + buttonWidth + columnGap, rowThreeY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Healing Items", preferences.summonHealingItems())), button -> {
            preferences.setSummonHealingItems(toggle(preferences.summonHealingItems()));
            if (parseBoolean(preferences.summonHealingItems(), false)) {
                preferences.setSummonSelfHeal("true");
            }
            preferences.save();
            init();
        }).bounds(left + 18, rowFourY, buttonWidth, 20).build());
    }

    private void initFormationStep(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Formation: " + preferences.summonFormation()), button -> {
            preferences.setSummonFormation(cycle(FORMATIONS, preferences.summonFormation()));
            preferences.save();
            init();
        }).bounds(left + 18, top + 120, 188, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xF0111418, 0xF01A2028);
        guiGraphics.fill(panelLeft(), panelTop(), panelRight(), panelBottom(), 0xC0151D25);
        guiGraphics.fill(panelLeft() + 10, panelTop() + 10, panelRight() - 10, panelBottom() - 10, 0xA01C2630);

        int left = panelLeft() + 18;
        int top = panelTop() + 18;

        guiGraphics.drawString(font, "PlayerBatch Summon Wizard", left, top, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Step " + step.number + " of 4: " + step.title, left, top + 18, 0xFF9BE5B8);

        switch (step) {
            case BASICS -> renderBasics(guiGraphics, left, top + 72);
            case LOADOUT -> renderLoadout(guiGraphics, left, top + 72);
            case ARGUMENTS -> renderArguments(guiGraphics, left, top + 72);
            case FORMATION -> renderFormation(guiGraphics, left, top + 72);
        }

        renderStatus(guiGraphics, left, panelBottom() - 78);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderBasics(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Write how many bots you want and any fixed names you want to reserve.", left, top, 0xFFC3CED7);
        guiGraphics.drawString(font, "Count", left, top + 18, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Names (comma-separated, no UI limit)", left + 102, top + 18, 0xFFEBDCA9);
        guiGraphics.drawString(font, basicsSummary(), left, top + 56, 0xFFD9E4F1);
    }

    private void renderLoadout(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Pick a simple default armor tier and tool tier for the batch.", left, top, 0xFFC3CED7);
        guiGraphics.drawString(font, "Armor Level", left, top + 18, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Tools", left + 182, top + 18, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Selected armor: " + pretty(preferences.summonArmorMaterial()), left, top + 58, 0xFFD9E4F1);
        guiGraphics.drawString(font, "Selected tools: " + pretty(preferences.summonToolMaterial()), left, top + 74, 0xFFD9E4F1);
    }

    private void renderArguments(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Toggle the combat-style arguments you want on the summoned bots.", left, top, 0xFFC3CED7);
        guiGraphics.drawString(font, "Everything here feeds the backend preset logic, not fake GUI-only state.", left, top + 14, 0xFF8FB7D1);
        guiGraphics.drawString(font, "Reach / fake hit / STAP / damage / 360 flex / self heal / healing items", left, top + 38, 0xFFD9E4F1);
        guiGraphics.drawString(font, "If Damage is ON, Fake Hit is forced ON so bots can actually hurt players.", left, top + 52, 0xFFEBDCA9);
    }

    private void renderFormation(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Pick the spawn formation, then click Summon Bots.", left, top, 0xFFC3CED7);
        guiGraphics.drawString(font, "Formation", left, top + 18, 0xFFEBDCA9);
        guiGraphics.drawString(font, finalSummary(), left, top + 56, 0xFFD9E4F1);
    }

    private void renderStatus(GuiGraphics guiGraphics, int left, int y) {
        guiGraphics.drawString(font,
                "Batch: " + (snapshot.batchActive() ? "Active" : "Idle")
                        + " | Queued: " + snapshot.queuedBatches()
                        + " | Selected: " + snapshot.selectedNames().size(),
                left,
                y,
                0xFFEBDCA9);
        guiGraphics.drawString(font,
                "Progress: " + snapshot.successCount() + "/" + snapshot.totalCount()
                        + " success, " + snapshot.failCount() + " failed, " + snapshot.pendingCount() + " pending",
                left,
                y + 14,
                0xFFC3CED7);
    }

    private EditBox addBox(int x, int y, int width, String value, java.util.function.Consumer<String> responder) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.empty()));
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        return box;
    }

    private void advance() {
        saveDraft();
        if (step == WizardStep.FORMATION) {
            summonBatch();
            return;
        }
        step = WizardStep.values()[step.ordinal() + 1];
        preferences.setSummonPane(step.preferenceValue);
        preferences.save();
        init();
    }

    private void goBack() {
        saveDraft();
        if (step.ordinal() > 0) {
            step = WizardStep.values()[step.ordinal() - 1];
            preferences.setSummonPane(step.preferenceValue);
            preferences.save();
            init();
        }
    }

    private void summonBatch() {
        saveDraft();
        int count = Math.max(parseInt(preferences.summonCount(), 1), countNames(preferences.summonNames()));
        BotConfig config = buildWizardConfig();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SUMMON,
                preferences.summonNames(),
                config.encode(),
                count,
                false
        ));
        resetWizard();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    private BotConfig buildWizardConfig() {
        BotLoadout loadout = new BotLoadout();
        applyArmorLoadout(loadout, preferences.summonArmorMaterial());
        applyToolLoadout(loadout, preferences.summonToolMaterial());
        CombatPresetSpec combatPreset = new CombatPresetSpec(
                CombatPresetSpec.ArmorTier.NONE,
                CombatPresetSpec.ToolTier.NONE,
                CombatPresetSpec.OffhandMode.SHIELD,
                1,
                parseBoolean(preferences.summonSelfHeal(), false),
                parseBoolean(preferences.summonHealingItems(), false),
                List.of(),
                parseInt(preferences.summonReach(), 3),
                parseBoolean(preferences.summonFakeHit(), true),
                parseBoolean(preferences.summonStap(), false),
                parseBoolean(preferences.summonDamage(), true),
                parseBoolean(preferences.summon360Flex(), false)
        );
        return new BotConfig(preferences.summonFormation(), loadout, combatPreset);
    }

    private void applyArmorLoadout(BotLoadout loadout, String material) {
        String normalized = normalize(material);
        if ("none".equals(normalized)) {
            return;
        }
        loadout.equipment().put(EquipmentSlot.HEAD, new BotLoadout.StackSpec("minecraft:" + normalized + "_helmet", 1));
        loadout.equipment().put(EquipmentSlot.CHEST, new BotLoadout.StackSpec("minecraft:" + normalized + "_chestplate", 1));
        loadout.equipment().put(EquipmentSlot.LEGS, new BotLoadout.StackSpec("minecraft:" + normalized + "_leggings", 1));
        loadout.equipment().put(EquipmentSlot.FEET, new BotLoadout.StackSpec("minecraft:" + normalized + "_boots", 1));
    }

    private void applyToolLoadout(BotLoadout loadout, String material) {
        String normalized = normalize(material);
        if ("none".equals(normalized)) {
            return;
        }
        loadout.equipment().put(EquipmentSlot.MAINHAND, new BotLoadout.StackSpec("minecraft:" + normalized + "_sword", 1));
        loadout.hotbar().put(1, new BotLoadout.StackSpec("minecraft:" + normalized + "_axe", 1));
    }

    private void saveBasicsDraft(String ignored) {
        saveDraft();
    }

    private void saveDraft() {
        preferences.setSummonPane(step.preferenceValue);
        if (countBox != null) {
            preferences.setSummonCount(countBox.getValue().trim());
        }
        if (namesBox != null) {
            preferences.setSummonNames(namesBox.getValue().trim());
        }
        preferences.save();
    }

    private String basicsSummary() {
        int customNames = countNames(preferences.summonNames());
        int finalCount = Math.max(parseInt(preferences.summonCount(), 1), customNames);
        return "Custom names: " + customNames + " | Final bot count: " + finalCount;
    }

    private String finalSummary() {
        int customNames = countNames(preferences.summonNames());
        int finalCount = Math.max(parseInt(preferences.summonCount(), 1), customNames);
        return "Ready to summon " + finalCount
                + " bots with "
                + pretty(preferences.summonArmorMaterial()) + " armor, "
                + pretty(preferences.summonToolMaterial()) + " tools, "
                + preferences.summonFormation() + " formation.";
    }

    private int countNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String part : raw.split(",")) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw);
    }

    private String toggle(String current) {
        return Boolean.toString(!parseBoolean(current, false));
    }

    private String nextReach(String current) {
        int reach = parseInt(current, 3);
        reach++;
        if (reach > 10) {
            reach = 1;
        }
        return Integer.toString(reach);
    }

    private String cycle(List<String> values, String current) {
        String normalized = normalize(current);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equalsIgnoreCase(normalized)) {
                return values.get((index + 1) % values.size());
            }
        }
        return values.getFirst();
    }

    private String toggleLabel(String label, String value) {
        return label + ": " + (parseBoolean(value, false) ? "ON" : "OFF");
    }

    private String pretty(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return "None";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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

    public void applySnapshot(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void onClose() {
        saveDraft();
        resetWizard();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    private void resetWizard() {
        step = WizardStep.BASICS;
        preferences.setSummonPane(WizardStep.BASICS.preferenceValue);
        preferences.save();
    }

    private int panelLeft() {
        return 14;
    }

    private int panelTop() {
        return 14;
    }

    private int panelRight() {
        return width - 14;
    }

    private int panelBottom() {
        return height - 14;
    }

    private int panelWidth() {
        return panelRight() - panelLeft();
    }

    private enum WizardStep {
        BASICS("BASICS", 1, "Bot Count & Names"),
        LOADOUT("LOADOUT", 2, "Armor & Tools"),
        ARGUMENTS("ARGUMENTS", 3, "Arguments"),
        FORMATION("FORMATION", 4, "Formation & Summon");

        private final String preferenceValue;
        private final int number;
        private final String title;

        WizardStep(String preferenceValue, int number, String title) {
            this.preferenceValue = preferenceValue;
            this.number = number;
            this.title = title;
        }

        private static WizardStep fromPreference(String raw) {
            for (WizardStep value : values()) {
                if (value.preferenceValue.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return BASICS;
        }
    }
}
