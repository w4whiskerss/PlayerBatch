package com.zahen.playerbatch.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PlayerBatchUiPreferences {
    private static final String FILE_NAME = "playerbatch-ui.properties";

    private static final String KEY_ACTIVE_TAB = "activeTab";
    private static final String KEY_SUMMON_COUNT = "summonCount";
    private static final String KEY_SUMMON_NAMES = "summonNames";
    private static final String KEY_SUMMON_FORMATION = "summonFormation";
    private static final String KEY_SUMMON_PANE = "summonPane";
    private static final String KEY_SUMMON_HEAD = "summonHead";
    private static final String KEY_SUMMON_CHEST = "summonChest";
    private static final String KEY_SUMMON_LEGS = "summonLegs";
    private static final String KEY_SUMMON_FEET = "summonFeet";
    private static final String KEY_SUMMON_MAINHAND = "summonMainhand";
    private static final String KEY_SUMMON_OFFHAND = "summonOffhand";
    private static final String KEY_SUMMON_HOTBAR = "summonHotbar";
    private static final String KEY_SUMMON_EFFECT_ID = "summonEffectId";
    private static final String KEY_SUMMON_EFFECT_DURATION = "summonEffectDuration";
    private static final String KEY_SUMMON_EFFECT_AMPLIFIER = "summonEffectAmplifier";
    private static final String KEY_SELECT_RANGE = "selectRange";
    private static final String KEY_SELECT_CLOSEST = "selectClosest";
    private static final String KEY_GROUP_NAME = "groupName";
    private static final String KEY_AI_MODE = "aiMode";
    private static final String KEY_ITEM_SLOT = "itemSlot";
    private static final String KEY_ITEM_ID = "itemId";
    private static final String KEY_ITEM_COUNT = "itemCount";
    private static final String KEY_EFFECT_ID = "effectId";
    private static final String KEY_EFFECT_DURATION = "effectDuration";
    private static final String KEY_EFFECT_AMPLIFIER = "effectAmplifier";
    private static final String KEY_ACTION = "action";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_BLOCK = "block";

    private final Path filePath;

    private String activeTab = "SUMMONING";
    private String summonCount = "8";
    private String summonNames = "";
    private String summonFormation = "circle";
    private String summonPane = "SETUP";
    private String summonHead = "";
    private String summonChest = "";
    private String summonLegs = "";
    private String summonFeet = "";
    private String summonMainhand = "diamond_sword";
    private String summonOffhand = "";
    private String summonHotbar = "";
    private String summonEffectId = "speed";
    private String summonEffectDuration = "30";
    private String summonEffectAmplifier = "0";
    private String selectRange = "16";
    private String selectClosest = "10";
    private String groupName = "alpha";
    private String aiMode = "idle";
    private String itemSlot = "mainhand";
    private String itemId = "diamond_sword";
    private String itemCount = "1";
    private String effectId = "speed";
    private String effectDuration = "30";
    private String effectAmplifier = "0";
    private String action = "attack once";
    private String direction = "up";
    private String block = "stone";

    private PlayerBatchUiPreferences(Path filePath) {
        this.filePath = filePath;
    }

    public static PlayerBatchUiPreferences load() {
        Path filePath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        PlayerBatchUiPreferences preferences = new PlayerBatchUiPreferences(filePath);
        if (!Files.exists(filePath)) {
            return preferences;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            properties.load(inputStream);
        } catch (IOException ignored) {
            return preferences;
        }

        preferences.activeTab = read(properties, KEY_ACTIVE_TAB, preferences.activeTab);
        preferences.summonCount = read(properties, KEY_SUMMON_COUNT, preferences.summonCount);
        preferences.summonNames = read(properties, KEY_SUMMON_NAMES, preferences.summonNames);
        preferences.summonFormation = read(properties, KEY_SUMMON_FORMATION, preferences.summonFormation);
        preferences.summonPane = read(properties, KEY_SUMMON_PANE, preferences.summonPane);
        preferences.summonHead = read(properties, KEY_SUMMON_HEAD, preferences.summonHead);
        preferences.summonChest = read(properties, KEY_SUMMON_CHEST, preferences.summonChest);
        preferences.summonLegs = read(properties, KEY_SUMMON_LEGS, preferences.summonLegs);
        preferences.summonFeet = read(properties, KEY_SUMMON_FEET, preferences.summonFeet);
        preferences.summonMainhand = read(properties, KEY_SUMMON_MAINHAND, preferences.summonMainhand);
        preferences.summonOffhand = read(properties, KEY_SUMMON_OFFHAND, preferences.summonOffhand);
        preferences.summonHotbar = read(properties, KEY_SUMMON_HOTBAR, preferences.summonHotbar);
        preferences.summonEffectId = read(properties, KEY_SUMMON_EFFECT_ID, preferences.summonEffectId);
        preferences.summonEffectDuration = read(properties, KEY_SUMMON_EFFECT_DURATION, preferences.summonEffectDuration);
        preferences.summonEffectAmplifier = read(properties, KEY_SUMMON_EFFECT_AMPLIFIER, preferences.summonEffectAmplifier);
        preferences.selectRange = read(properties, KEY_SELECT_RANGE, preferences.selectRange);
        preferences.selectClosest = read(properties, KEY_SELECT_CLOSEST, preferences.selectClosest);
        preferences.groupName = read(properties, KEY_GROUP_NAME, preferences.groupName);
        preferences.aiMode = read(properties, KEY_AI_MODE, preferences.aiMode);
        preferences.itemSlot = read(properties, KEY_ITEM_SLOT, preferences.itemSlot);
        preferences.itemId = read(properties, KEY_ITEM_ID, preferences.itemId);
        preferences.itemCount = read(properties, KEY_ITEM_COUNT, preferences.itemCount);
        preferences.effectId = read(properties, KEY_EFFECT_ID, preferences.effectId);
        preferences.effectDuration = read(properties, KEY_EFFECT_DURATION, preferences.effectDuration);
        preferences.effectAmplifier = read(properties, KEY_EFFECT_AMPLIFIER, preferences.effectAmplifier);
        preferences.action = read(properties, KEY_ACTION, preferences.action);
        preferences.direction = read(properties, KEY_DIRECTION, preferences.direction);
        preferences.block = read(properties, KEY_BLOCK, preferences.block);
        return preferences;
    }

    private static String read(Properties properties, String key, String fallback) {
        return properties.getProperty(key, fallback);
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_ACTIVE_TAB, activeTab);
        properties.setProperty(KEY_SUMMON_COUNT, summonCount);
        properties.setProperty(KEY_SUMMON_NAMES, summonNames);
        properties.setProperty(KEY_SUMMON_FORMATION, summonFormation);
        properties.setProperty(KEY_SUMMON_PANE, summonPane);
        properties.setProperty(KEY_SUMMON_HEAD, summonHead);
        properties.setProperty(KEY_SUMMON_CHEST, summonChest);
        properties.setProperty(KEY_SUMMON_LEGS, summonLegs);
        properties.setProperty(KEY_SUMMON_FEET, summonFeet);
        properties.setProperty(KEY_SUMMON_MAINHAND, summonMainhand);
        properties.setProperty(KEY_SUMMON_OFFHAND, summonOffhand);
        properties.setProperty(KEY_SUMMON_HOTBAR, summonHotbar);
        properties.setProperty(KEY_SUMMON_EFFECT_ID, summonEffectId);
        properties.setProperty(KEY_SUMMON_EFFECT_DURATION, summonEffectDuration);
        properties.setProperty(KEY_SUMMON_EFFECT_AMPLIFIER, summonEffectAmplifier);
        properties.setProperty(KEY_SELECT_RANGE, selectRange);
        properties.setProperty(KEY_SELECT_CLOSEST, selectClosest);
        properties.setProperty(KEY_GROUP_NAME, groupName);
        properties.setProperty(KEY_AI_MODE, aiMode);
        properties.setProperty(KEY_ITEM_SLOT, itemSlot);
        properties.setProperty(KEY_ITEM_ID, itemId);
        properties.setProperty(KEY_ITEM_COUNT, itemCount);
        properties.setProperty(KEY_EFFECT_ID, effectId);
        properties.setProperty(KEY_EFFECT_DURATION, effectDuration);
        properties.setProperty(KEY_EFFECT_AMPLIFIER, effectAmplifier);
        properties.setProperty(KEY_ACTION, action);
        properties.setProperty(KEY_DIRECTION, direction);
        properties.setProperty(KEY_BLOCK, block);

        try {
            Files.createDirectories(filePath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                properties.store(outputStream, "PlayerBatch UI preferences");
            }
        } catch (IOException ignored) {
        }
    }

    public String activeTab() {
        return activeTab;
    }

    public void setActiveTab(String activeTab) {
        this.activeTab = activeTab;
    }

    public String summonCount() {
        return summonCount;
    }

    public void setSummonCount(String summonCount) {
        this.summonCount = summonCount;
    }

    public String summonNames() {
        return summonNames;
    }

    public void setSummonNames(String summonNames) {
        this.summonNames = summonNames;
    }

    public String summonFormation() {
        return summonFormation;
    }

    public void setSummonFormation(String summonFormation) {
        this.summonFormation = summonFormation;
    }

    public String summonPane() {
        return summonPane;
    }

    public void setSummonPane(String summonPane) {
        this.summonPane = summonPane;
    }

    public String summonHead() {
        return summonHead;
    }

    public void setSummonHead(String summonHead) {
        this.summonHead = summonHead;
    }

    public String summonChest() {
        return summonChest;
    }

    public void setSummonChest(String summonChest) {
        this.summonChest = summonChest;
    }

    public String summonLegs() {
        return summonLegs;
    }

    public void setSummonLegs(String summonLegs) {
        this.summonLegs = summonLegs;
    }

    public String summonFeet() {
        return summonFeet;
    }

    public void setSummonFeet(String summonFeet) {
        this.summonFeet = summonFeet;
    }

    public String summonMainhand() {
        return summonMainhand;
    }

    public void setSummonMainhand(String summonMainhand) {
        this.summonMainhand = summonMainhand;
    }

    public String summonOffhand() {
        return summonOffhand;
    }

    public void setSummonOffhand(String summonOffhand) {
        this.summonOffhand = summonOffhand;
    }

    public String summonHotbar() {
        return summonHotbar;
    }

    public void setSummonHotbar(String summonHotbar) {
        this.summonHotbar = summonHotbar;
    }

    public String summonEffectId() {
        return summonEffectId;
    }

    public void setSummonEffectId(String summonEffectId) {
        this.summonEffectId = summonEffectId;
    }

    public String summonEffectDuration() {
        return summonEffectDuration;
    }

    public void setSummonEffectDuration(String summonEffectDuration) {
        this.summonEffectDuration = summonEffectDuration;
    }

    public String summonEffectAmplifier() {
        return summonEffectAmplifier;
    }

    public void setSummonEffectAmplifier(String summonEffectAmplifier) {
        this.summonEffectAmplifier = summonEffectAmplifier;
    }

    public String selectRange() {
        return selectRange;
    }

    public void setSelectRange(String selectRange) {
        this.selectRange = selectRange;
    }

    public String selectClosest() {
        return selectClosest;
    }

    public void setSelectClosest(String selectClosest) {
        this.selectClosest = selectClosest;
    }

    public String groupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String aiMode() {
        return aiMode;
    }

    public void setAiMode(String aiMode) {
        this.aiMode = aiMode;
    }

    public String itemSlot() {
        return itemSlot;
    }

    public void setItemSlot(String itemSlot) {
        this.itemSlot = itemSlot;
    }

    public String itemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String itemCount() {
        return itemCount;
    }

    public void setItemCount(String itemCount) {
        this.itemCount = itemCount;
    }

    public String effectId() {
        return effectId;
    }

    public void setEffectId(String effectId) {
        this.effectId = effectId;
    }

    public String effectDuration() {
        return effectDuration;
    }

    public void setEffectDuration(String effectDuration) {
        this.effectDuration = effectDuration;
    }

    public String effectAmplifier() {
        return effectAmplifier;
    }

    public void setEffectAmplifier(String effectAmplifier) {
        this.effectAmplifier = effectAmplifier;
    }

    public String action() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String direction() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String block() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }
}
