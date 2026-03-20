package com.zahen.playerbatch.client.gui;

import com.zahen.playerbatch.client.PlayerBatchClient;
import com.zahen.playerbatch.client.PlayerBatchUiPreferences;
import com.zahen.playerbatch.core.BotConfig;
import com.zahen.playerbatch.core.BotLoadout;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlayerBatchScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 500;
    private static final List<String> FORMATIONS = List.of("circle", "square", "triangle", "random", "single block");

    private final Screen parent;
    private final PlayerBatchUiPreferences preferences;
    private PlayerBatchService.PlayerBatchSnapshot snapshot;

    private Page activePage;
    private EditBox countBox;
    private EditBox namesBox;
    private EditBox headBox;
    private EditBox chestBox;
    private EditBox legsBox;
    private EditBox feetBox;
    private EditBox mainhandBox;
    private EditBox offhandBox;
    private EditBox hotbarBox;
    private EditBox inventoryBox;
    private EditBox effectIdBox;
    private EditBox effectDurationBox;
    private EditBox effectAmplifierBox;
    private EditBox distributionOnePercentBox;
    private EditBox distributionOneArmorBox;
    private EditBox distributionOneWeaponBox;
    private EditBox distributionTwoPercentBox;
    private EditBox distributionTwoArmorBox;
    private EditBox distributionTwoWeaponBox;
    private EditBox distributionThreePercentBox;
    private EditBox distributionThreeArmorBox;
    private EditBox distributionThreeWeaponBox;
    private Button summonButton;
    private Button formationButton;
    private SummonSummary summary = SummonSummary.empty();

    public PlayerBatchScreen(Screen parent) {
        super(Component.literal("PlayerBatch"));
        this.parent = parent;
        this.preferences = PlayerBatchUiPreferences.load();
        this.snapshot = PlayerBatchClient.latestSnapshot();
        this.activePage = Page.fromPreference(preferences.activeTab());
    }

    @Override
    protected void init() {
        clearWidgets();
        addPageButtons();
        addFooterButtons();
        switch (activePage) {
            case BOT_SUMMONING -> initBotSummoningPage();
            case ACTIONS -> initActionsPage();
            case DEBUGGING -> initDebugPage();
        }
        requestState(false);
    }

    private void addPageButtons() {
        int left = panelLeft() + 18;
        int top = panelTop() + 16;
        int width = 144;
        int gap = 8;
        int index = 0;
        for (Page page : Page.values()) {
            int x = left + index * (width + gap);
            addRenderableWidget(Button.builder(page.label(activePage == page), button -> switchPage(page))
                    .bounds(x, top, width, 20).build());
            index++;
        }
    }

    private void addFooterButtons() {
        int left = panelLeft() + 18;
        int y = panelTop() + PANEL_HEIGHT - 30;
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> requestState(false))
                .bounds(left + 88, y, 84, 20).build());
    }

    private void initBotSummoningPage() {
        int left = panelLeft() + 18;
        int top = panelTop() + 86;

        countBox = addBox(left, top + 22, 70, preferences.summonCount(), this::refreshSummonState);
        namesBox = addBox(left + 82, top + 22, 402, preferences.summonNames(), value -> {
            autoGrowCount();
            refreshSummonState(value);
        });

        headBox = addBox(left, top + 114, 116, preferences.summonHead(), value -> saveDraft());
        chestBox = addBox(left + 124, top + 114, 116, preferences.summonChest(), value -> saveDraft());
        legsBox = addBox(left + 248, top + 114, 116, preferences.summonLegs(), value -> saveDraft());
        feetBox = addBox(left + 372, top + 114, 112, preferences.summonFeet(), value -> saveDraft());

        mainhandBox = addBox(left, top + 164, 116, preferences.summonMainhand(), value -> saveDraft());
        offhandBox = addBox(left + 124, top + 164, 116, preferences.summonOffhand(), value -> saveDraft());
        effectIdBox = addBox(left + 248, top + 164, 116, preferences.summonEffectId(), value -> saveDraft());
        effectDurationBox = addBox(left + 372, top + 164, 52, preferences.summonEffectDuration(), value -> saveDraft());
        effectAmplifierBox = addBox(left + 432, top + 164, 52, preferences.summonEffectAmplifier(), value -> saveDraft());

        hotbarBox = addBox(left, top + 220, 484, preferences.summonHotbar(), value -> saveDraft());
        inventoryBox = addBox(left, top + 272, 484, preferences.summonInventory(), value -> saveDraft());

        distributionOnePercentBox = addBox(left, top + 360, 52, preferences.distributionOnePercent(), value -> saveDraft());
        distributionOneArmorBox = addBox(left + 60, top + 360, 130, preferences.distributionOneArmor(), value -> saveDraft());
        distributionOneWeaponBox = addBox(left + 198, top + 360, 132, preferences.distributionOneWeapon(), value -> saveDraft());
        distributionTwoPercentBox = addBox(left + 338, top + 360, 52, preferences.distributionTwoPercent(), value -> saveDraft());
        distributionTwoArmorBox = addBox(left + 398, top + 360, 86, preferences.distributionTwoArmor(), value -> saveDraft());

        distributionTwoWeaponBox = addBox(left, top + 392, 132, preferences.distributionTwoWeapon(), value -> saveDraft());
        distributionThreePercentBox = addBox(left + 140, top + 392, 52, preferences.distributionThreePercent(), value -> saveDraft());
        distributionThreeArmorBox = addBox(left + 200, top + 392, 130, preferences.distributionThreeArmor(), value -> saveDraft());
        distributionThreeWeaponBox = addBox(left + 338, top + 392, 146, preferences.distributionThreeWeapon(), value -> saveDraft());

        formationButton = addRenderableWidget(Button.builder(Component.literal("Formation: " + preferences.summonFormation()), button -> {
            saveDraft();
            preferences.setSummonFormation(nextFormation(preferences.summonFormation()));
            preferences.save();
            init();
        }).bounds(left + 278, panelTop() + PANEL_HEIGHT - 34, 148, 20).build());

        summonButton = addRenderableWidget(Button.builder(Component.literal("Summon"), button -> summonBatch())
                .bounds(left + 434, panelTop() + PANEL_HEIGHT - 34, 50, 20).build());

        refreshSummonState("");
    }

    private void initActionsPage() {
        int left = panelLeft() + 18;
        int top = panelTop() + 74;
        addRenderableWidget(Button.builder(Component.literal("Open Bot Summoning"), button -> switchPage(Page.BOT_SUMMONING))
                .bounds(left, top + 88, 140, 20).build());
    }

    private void initDebugPage() {
        int left = panelLeft() + 18;
        int top = panelTop() + 74;
        addRenderableWidget(Button.builder(Component.literal("Open Bot Summoning"), button -> switchPage(Page.BOT_SUMMONING))
                .bounds(left, top + 104, 140, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xF0111418, 0xF01A2028);

        int left = panelLeft();
        int top = panelTop();
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xC0151D25);
        guiGraphics.fill(left + 10, top + 10, left + PANEL_WIDTH - 10, top + PANEL_HEIGHT - 10, 0xA01C2630);

        guiGraphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFFFF);
        switch (activePage) {
            case BOT_SUMMONING -> renderBotSummoning(guiGraphics, left + 18, top + 86);
            case ACTIONS -> renderActions(guiGraphics, left + 18, top + 74);
            case DEBUGGING -> renderDebug(guiGraphics, left + 18, top + 74);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderBotSummoning(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Page 1: Bot Summoning", left, top, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Usernames are fixed. Remaining bots stay random.", left, top + 14, 0xFFC3CED7);
        guiGraphics.drawString(font, "Setup is still applied after spawn settles.", left, top + 26, 0xFFC3CED7);

        guiGraphics.drawString(font, "Count", left, top + 46, 0xFF9BE5B8);
        guiGraphics.drawString(font, "Custom usernames (comma-separated)", left + 82, top + 46, 0xFF9BE5B8);

        guiGraphics.drawString(font, "Live preview", left, top + 82, 0xFF9BE5B8);
        guiGraphics.drawString(font, "Custom: " + summary.customCount() + " | Random: " + summary.randomCount + " | Final: " + summary.finalCount, left, top + 96, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Parsed names: " + summary.previewNames(), left, top + 110, 0xFFD9E4F1);
        guiGraphics.drawString(font, summary.message, left, top + 432, summary.valid ? 0xFFBFD8E8 : 0xFFFF9696);

        guiGraphics.drawString(font, "Helmet", left, top + 134, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Chestplate", left + 124, top + 134, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Leggings", left + 248, top + 134, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Boots", left + 372, top + 134, 0xFFEBDCA9);

        guiGraphics.drawString(font, "Main hand", left, top + 184, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Offhand", left + 124, top + 184, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Effect", left + 248, top + 184, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Sec", left + 372, top + 184, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Amp", left + 432, top + 184, 0xFFEBDCA9);

        guiGraphics.drawString(font, "Hotbar CSV 1-9", left, top + 212, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Example: diamond_sword, bow, bread*8", left + 110, top + 212, 0xFF8FB7D1);

        guiGraphics.drawString(font, "Backpack CSV 10-36", left, top + 264, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Example: cobblestone*64, torch*32", left + 128, top + 264, 0xFF8FB7D1);

        guiGraphics.drawString(font, "Distribution groups", left, top + 328, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Group 1: % / armor / weapon", left, top + 342, 0xFF8FB7D1);
        guiGraphics.drawString(font, "Group 2: % / armor", left + 338, top + 342, 0xFF8FB7D1);
        guiGraphics.drawString(font, "Group 2 weapon / Group 3: % / armor / weapon", left, top + 374, 0xFF8FB7D1);
    }

    private void renderActions(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Page 2: Actions", left, top, 0xFFEBDCA9);
        guiGraphics.drawString(font, "This page is reserved for bot actions and command tools.", left, top + 18, 0xFFC3CED7);
        guiGraphics.drawString(font, "Current selected bots: " + snapshot.selectedNames().size(), left, top + 36, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Summoning is now handled entirely on page 1.", left, top + 52, 0xFFD9E4F1);
    }

    private void renderDebug(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(font, "Page 3: Debugging", left, top, 0xFFEBDCA9);
        guiGraphics.drawString(font, "Batch active: " + snapshot.batchActive(), left, top + 18, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Queued batches: " + snapshot.queuedBatches(), left, top + 34, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Progress: " + snapshot.successCount() + "/" + snapshot.totalCount()
                + " success, " + snapshot.failCount() + " failed, " + snapshot.pendingCount() + " pending", left, top + 50, 0xFFD9E4F1);
        guiGraphics.drawString(font, "Debug tools stay here. Summon controls stay on page 1.", left, top + 68, 0xFFC3CED7);
    }

    private EditBox addBox(int x, int y, int width, String value, java.util.function.Consumer<String> responder) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.empty()));
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        return box;
    }

    private void refreshSummonState(String ignored) {
        autoGrowCount();
        saveDraft();
        summary = buildSummary();
        if (summonButton != null) {
            summonButton.active = summary.valid;
        }
    }

    private SummonSummary buildSummary() {
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String duplicate = null;
        String invalid = null;

        String rawNames = namesBox == null ? preferences.summonNames() : namesBox.getValue();
        for (String piece : rawNames.split(",")) {
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
            names.add(candidate);
        }

        int finalCount = Math.max(parseInt(value(countBox, preferences.summonCount()), 1), names.size());
        int randomCount = Math.max(0, finalCount - names.size());
        String message = "Ready to summon " + finalCount + " bots in " + preferences.summonFormation() + " formation.";
        boolean valid = true;

        if (duplicate != null) {
            valid = false;
            message = "Duplicate username: " + duplicate;
        } else if (invalid != null) {
            valid = false;
            message = "Invalid username: " + invalid + " (3-16 letters, numbers, or _)";
        } else if (!distributionLooksValid()) {
            valid = false;
            message = "Distribution groups must total 100% or less.";
        }

        return new SummonSummary(names, finalCount, randomCount, valid, message);
    }

    private boolean distributionLooksValid() {
        int total = parseInt(value(distributionOnePercentBox, preferences.distributionOnePercent()), 0)
                + parseInt(value(distributionTwoPercentBox, preferences.distributionTwoPercent()), 0)
                + parseInt(value(distributionThreePercentBox, preferences.distributionThreePercent()), 0);
        return total <= 100;
    }

    private void summonBatch() {
        refreshSummonState("");
        if (!summary.valid) {
            return;
        }
        BotConfig config = buildSummonConfig();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(
                PlayerBatchNetworking.ActionKind.SUMMON,
                String.join(", ", summary.names),
                config.encode(),
                summary.finalCount,
                false
        ));
    }

    private BotConfig buildSummonConfig() {
        BotLoadout baseLoadout = new BotLoadout();
        putEquipment(baseLoadout, EquipmentSlot.HEAD, value(headBox, preferences.summonHead()));
        putEquipment(baseLoadout, EquipmentSlot.CHEST, value(chestBox, preferences.summonChest()));
        putEquipment(baseLoadout, EquipmentSlot.LEGS, value(legsBox, preferences.summonLegs()));
        putEquipment(baseLoadout, EquipmentSlot.FEET, value(feetBox, preferences.summonFeet()));
        putEquipment(baseLoadout, EquipmentSlot.MAINHAND, value(mainhandBox, preferences.summonMainhand()));
        putEquipment(baseLoadout, EquipmentSlot.OFFHAND, value(offhandBox, preferences.summonOffhand()));
        applyInventoryCsv(baseLoadout.hotbar(), value(hotbarBox, preferences.summonHotbar()), 0);
        applyInventoryCsv(baseLoadout.inventory(), value(inventoryBox, preferences.summonInventory()), 9);
        addEffect(baseLoadout, value(effectIdBox, preferences.summonEffectId()), value(effectDurationBox, preferences.summonEffectDuration()), value(effectAmplifierBox, preferences.summonEffectAmplifier()));

        List<BotConfig.DistributionRule> distributions = new ArrayList<>();
        addDistribution(distributions, value(distributionOnePercentBox, preferences.distributionOnePercent()), value(distributionOneArmorBox, preferences.distributionOneArmor()), value(distributionOneWeaponBox, preferences.distributionOneWeapon()));
        addDistribution(distributions, value(distributionTwoPercentBox, preferences.distributionTwoPercent()), value(distributionTwoArmorBox, preferences.distributionTwoArmor()), value(distributionTwoWeaponBox, preferences.distributionTwoWeapon()));
        addDistribution(distributions, value(distributionThreePercentBox, preferences.distributionThreePercent()), value(distributionThreeArmorBox, preferences.distributionThreeArmor()), value(distributionThreeWeaponBox, preferences.distributionThreeWeapon()));

        return new BotConfig(preferences.summonFormation(), baseLoadout, distributions);
    }

    private void addDistribution(List<BotConfig.DistributionRule> distributions, String percentRaw, String armorRaw, String weaponRaw) {
        int percent = parseInt(percentRaw, 0);
        BotLoadout loadout = armorLoadout(armorRaw);
        if (!weaponRaw.isBlank()) {
            loadout.equipment().put(EquipmentSlot.MAINHAND, new BotLoadout.StackSpec(normalizeItemId(weaponRaw), 1));
        }
        if (percent > 0 && !loadout.isEmpty()) {
            distributions.add(new BotConfig.DistributionRule(percent, loadout));
        }
    }

    private BotLoadout armorLoadout(String armorRaw) {
        BotLoadout loadout = new BotLoadout();
        String armor = armorRaw == null ? "" : armorRaw.trim().toLowerCase(Locale.ROOT);
        if (armor.isEmpty()) {
            return loadout;
        }
        if (List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite").contains(armor)) {
            loadout.equipment().put(EquipmentSlot.HEAD, new BotLoadout.StackSpec("minecraft:" + armor + "_helmet", 1));
            loadout.equipment().put(EquipmentSlot.CHEST, new BotLoadout.StackSpec("minecraft:" + armor + "_chestplate", 1));
            loadout.equipment().put(EquipmentSlot.LEGS, new BotLoadout.StackSpec("minecraft:" + armor + "_leggings", 1));
            loadout.equipment().put(EquipmentSlot.FEET, new BotLoadout.StackSpec("minecraft:" + armor + "_boots", 1));
        }
        return loadout;
    }

    private void addEffect(BotLoadout loadout, String effectId, String durationRaw, String ampRaw) {
        if (effectId.isBlank()) {
            return;
        }
        loadout.effects().add(new BotLoadout.EffectSpec(
                normalizeEffectId(effectId),
                Math.max(1, parseInt(durationRaw, 30)),
                Math.max(0, parseInt(ampRaw, 0))
        ));
    }

    private void applyInventoryCsv(java.util.Map<Integer, BotLoadout.StackSpec> target, String raw, int startSlot) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] parts = raw.split(",");
        for (int index = 0; index < parts.length; index++) {
            String token = parts[index].trim();
            if (token.isEmpty()) {
                continue;
            }
            int count = 1;
            String itemId = token;
            if (token.contains("*")) {
                String[] split = token.split("\\*", 2);
                itemId = split[0].trim();
                count = parseInt(split[1], 1);
            }
            target.put(startSlot + index, new BotLoadout.StackSpec(normalizeItemId(itemId), Math.max(1, count)));
        }
    }

    private void putEquipment(BotLoadout loadout, EquipmentSlot slot, String rawItem) {
        if (rawItem.isBlank()) {
            return;
        }
        loadout.equipment().put(slot, new BotLoadout.StackSpec(normalizeItemId(rawItem), 1));
    }

    private void autoGrowCount() {
        if (countBox == null || namesBox == null) {
            return;
        }
        int parsed = parseInt(countBox.getValue(), 1);
        int names = countNames(namesBox.getValue());
        if (names > parsed) {
            countBox.setValue(Integer.toString(names));
        }
    }

    private int countNames(String raw) {
        int count = 0;
        for (String piece : raw.split(",")) {
            if (!piece.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void switchPage(Page page) {
        saveDraft();
        activePage = page;
        preferences.setActiveTab(page.preferenceValue);
        preferences.save();
        init();
    }

    @Override
    public void onClose() {
        saveDraft();
        send(new PlayerBatchNetworking.PlayerBatchActionPayload(PlayerBatchNetworking.ActionKind.CLOSE_SCREEN, "", "", 0, false));
        Minecraft.getInstance().setScreen(parent);
    }

    public void applySnapshot(PlayerBatchService.PlayerBatchSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    private void saveDraft() {
        preferences.setActiveTab(activePage.preferenceValue);
        preferences.setSummonCount(value(countBox, preferences.summonCount()));
        preferences.setSummonNames(value(namesBox, preferences.summonNames()));
        preferences.setSummonHead(value(headBox, preferences.summonHead()));
        preferences.setSummonChest(value(chestBox, preferences.summonChest()));
        preferences.setSummonLegs(value(legsBox, preferences.summonLegs()));
        preferences.setSummonFeet(value(feetBox, preferences.summonFeet()));
        preferences.setSummonMainhand(value(mainhandBox, preferences.summonMainhand()));
        preferences.setSummonOffhand(value(offhandBox, preferences.summonOffhand()));
        preferences.setSummonHotbar(value(hotbarBox, preferences.summonHotbar()));
        preferences.setSummonInventory(value(inventoryBox, preferences.summonInventory()));
        preferences.setSummonEffectId(value(effectIdBox, preferences.summonEffectId()));
        preferences.setSummonEffectDuration(value(effectDurationBox, preferences.summonEffectDuration()));
        preferences.setSummonEffectAmplifier(value(effectAmplifierBox, preferences.summonEffectAmplifier()));
        preferences.setDistributionOnePercent(value(distributionOnePercentBox, preferences.distributionOnePercent()));
        preferences.setDistributionOneArmor(value(distributionOneArmorBox, preferences.distributionOneArmor()));
        preferences.setDistributionOneWeapon(value(distributionOneWeaponBox, preferences.distributionOneWeapon()));
        preferences.setDistributionTwoPercent(value(distributionTwoPercentBox, preferences.distributionTwoPercent()));
        preferences.setDistributionTwoArmor(value(distributionTwoArmorBox, preferences.distributionTwoArmor()));
        preferences.setDistributionTwoWeapon(value(distributionTwoWeaponBox, preferences.distributionTwoWeapon()));
        preferences.setDistributionThreePercent(value(distributionThreePercentBox, preferences.distributionThreePercent()));
        preferences.setDistributionThreeArmor(value(distributionThreeArmorBox, preferences.distributionThreeArmor()));
        preferences.setDistributionThreeWeapon(value(distributionThreeWeaponBox, preferences.distributionThreeWeapon()));
        preferences.save();
    }

    private String value(EditBox box, String fallback) {
        return box == null ? fallback : box.getValue().trim();
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalizeItemId(String raw) {
        String trimmed = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private String normalizeEffectId(String raw) {
        return normalizeItemId(raw);
    }

    private String nextFormation(String current) {
        for (int index = 0; index < FORMATIONS.size(); index++) {
            if (FORMATIONS.get(index).equalsIgnoreCase(current)) {
                return FORMATIONS.get((index + 1) % FORMATIONS.size());
            }
        }
        return FORMATIONS.get(0);
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

    private enum Page {
        BOT_SUMMONING("BOT_SUMMONING", "Bot Summoning"),
        ACTIONS("ACTIONS", "Actions"),
        DEBUGGING("DEBUGGING", "Debugging");

        private final String preferenceValue;
        private final String label;

        Page(String preferenceValue, String label) {
            this.preferenceValue = preferenceValue;
            this.label = label;
        }

        private static Page fromPreference(String raw) {
            for (Page page : values()) {
                if (page.preferenceValue.equalsIgnoreCase(raw)) {
                    return page;
                }
            }
            return BOT_SUMMONING;
        }

        private Component label(boolean active) {
            return Component.literal((active ? "> " : "") + label);
        }
    }

    private record SummonSummary(List<String> names, int finalCount, int randomCount, boolean valid, String message) {
        private static SummonSummary empty() {
            return new SummonSummary(List.of(), 1, 1, true, "Ready.");
        }

        private int customCount() {
            return names.size();
        }

        private String previewNames() {
            if (names.isEmpty()) {
                return "No custom names yet.";
            }
            if (names.size() <= 6) {
                return String.join(", ", names);
            }
            return String.join(", ", names.subList(0, 6)) + " ...";
        }
    }
}
