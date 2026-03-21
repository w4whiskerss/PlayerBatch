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
    private static final String KEY_SUMMON_ARMOR_MATERIAL = "summonArmorMaterial";
    private static final String KEY_SUMMON_TOOL_MATERIAL = "summonToolMaterial";
    private static final String KEY_SUMMON_REACH = "summonReach";
    private static final String KEY_SUMMON_FAKE_HIT = "summonFakeHit";
    private static final String KEY_SUMMON_STAP = "summonStap";
    private static final String KEY_SUMMON_DAMAGE = "summonDamage";
    private static final String KEY_SUMMON_360_FLEX = "summon360Flex";
    private static final String KEY_SUMMON_SELF_HEAL = "summonSelfHeal";
    private static final String KEY_SUMMON_HEALING_ITEMS = "summonHealingItems";
    private static final String KEY_SUMMON_HEAD = "summonHead";
    private static final String KEY_SUMMON_CHEST = "summonChest";
    private static final String KEY_SUMMON_LEGS = "summonLegs";
    private static final String KEY_SUMMON_FEET = "summonFeet";
    private static final String KEY_SUMMON_MAINHAND = "summonMainhand";
    private static final String KEY_SUMMON_OFFHAND = "summonOffhand";
    private static final String KEY_SUMMON_HOTBAR = "summonHotbar";
    private static final String KEY_SUMMON_INVENTORY = "summonInventory";
    private static final String KEY_SUMMON_EFFECT_ID = "summonEffectId";
    private static final String KEY_SUMMON_EFFECT_DURATION = "summonEffectDuration";
    private static final String KEY_SUMMON_EFFECT_AMPLIFIER = "summonEffectAmplifier";
    private static final String KEY_DISTRIBUTION_ONE_PERCENT = "distributionOnePercent";
    private static final String KEY_DISTRIBUTION_ONE_ARMOR = "distributionOneArmor";
    private static final String KEY_DISTRIBUTION_ONE_WEAPON = "distributionOneWeapon";
    private static final String KEY_DISTRIBUTION_TWO_PERCENT = "distributionTwoPercent";
    private static final String KEY_DISTRIBUTION_TWO_ARMOR = "distributionTwoArmor";
    private static final String KEY_DISTRIBUTION_TWO_WEAPON = "distributionTwoWeapon";
    private static final String KEY_DISTRIBUTION_THREE_PERCENT = "distributionThreePercent";
    private static final String KEY_DISTRIBUTION_THREE_ARMOR = "distributionThreeArmor";
    private static final String KEY_DISTRIBUTION_THREE_WEAPON = "distributionThreeWeapon";
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
    private String summonArmorMaterial = "diamond";
    private String summonToolMaterial = "diamond";
    private String summonReach = "3";
    private String summonFakeHit = "true";
    private String summonStap = "false";
    private String summonDamage = "true";
    private String summon360Flex = "false";
    private String summonSelfHeal = "false";
    private String summonHealingItems = "false";
    private String summonHead = "";
    private String summonChest = "";
    private String summonLegs = "";
    private String summonFeet = "";
    private String summonMainhand = "diamond_sword";
    private String summonOffhand = "";
    private String summonHotbar = "";
    private String summonInventory = "";
    private String summonEffectId = "speed";
    private String summonEffectDuration = "30";
    private String summonEffectAmplifier = "0";
    private String distributionOnePercent = "50";
    private String distributionOneArmor = "diamond";
    private String distributionOneWeapon = "diamond_sword";
    private String distributionTwoPercent = "30";
    private String distributionTwoArmor = "iron";
    private String distributionTwoWeapon = "iron_sword";
    private String distributionThreePercent = "20";
    private String distributionThreeArmor = "netherite";
    private String distributionThreeWeapon = "netherite_sword";
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
        preferences.summonArmorMaterial = read(properties, KEY_SUMMON_ARMOR_MATERIAL, preferences.summonArmorMaterial);
        preferences.summonToolMaterial = read(properties, KEY_SUMMON_TOOL_MATERIAL, preferences.summonToolMaterial);
        preferences.summonReach = read(properties, KEY_SUMMON_REACH, preferences.summonReach);
        preferences.summonFakeHit = read(properties, KEY_SUMMON_FAKE_HIT, preferences.summonFakeHit);
        preferences.summonStap = read(properties, KEY_SUMMON_STAP, preferences.summonStap);
        preferences.summonDamage = read(properties, KEY_SUMMON_DAMAGE, preferences.summonDamage);
        preferences.summon360Flex = read(properties, KEY_SUMMON_360_FLEX, preferences.summon360Flex);
        preferences.summonSelfHeal = read(properties, KEY_SUMMON_SELF_HEAL, preferences.summonSelfHeal);
        preferences.summonHealingItems = read(properties, KEY_SUMMON_HEALING_ITEMS, preferences.summonHealingItems);
        preferences.summonHead = read(properties, KEY_SUMMON_HEAD, preferences.summonHead);
        preferences.summonChest = read(properties, KEY_SUMMON_CHEST, preferences.summonChest);
        preferences.summonLegs = read(properties, KEY_SUMMON_LEGS, preferences.summonLegs);
        preferences.summonFeet = read(properties, KEY_SUMMON_FEET, preferences.summonFeet);
        preferences.summonMainhand = read(properties, KEY_SUMMON_MAINHAND, preferences.summonMainhand);
        preferences.summonOffhand = read(properties, KEY_SUMMON_OFFHAND, preferences.summonOffhand);
        preferences.summonHotbar = read(properties, KEY_SUMMON_HOTBAR, preferences.summonHotbar);
        preferences.summonInventory = read(properties, KEY_SUMMON_INVENTORY, preferences.summonInventory);
        preferences.summonEffectId = read(properties, KEY_SUMMON_EFFECT_ID, preferences.summonEffectId);
        preferences.summonEffectDuration = read(properties, KEY_SUMMON_EFFECT_DURATION, preferences.summonEffectDuration);
        preferences.summonEffectAmplifier = read(properties, KEY_SUMMON_EFFECT_AMPLIFIER, preferences.summonEffectAmplifier);
        preferences.distributionOnePercent = read(properties, KEY_DISTRIBUTION_ONE_PERCENT, preferences.distributionOnePercent);
        preferences.distributionOneArmor = read(properties, KEY_DISTRIBUTION_ONE_ARMOR, preferences.distributionOneArmor);
        preferences.distributionOneWeapon = read(properties, KEY_DISTRIBUTION_ONE_WEAPON, preferences.distributionOneWeapon);
        preferences.distributionTwoPercent = read(properties, KEY_DISTRIBUTION_TWO_PERCENT, preferences.distributionTwoPercent);
        preferences.distributionTwoArmor = read(properties, KEY_DISTRIBUTION_TWO_ARMOR, preferences.distributionTwoArmor);
        preferences.distributionTwoWeapon = read(properties, KEY_DISTRIBUTION_TWO_WEAPON, preferences.distributionTwoWeapon);
        preferences.distributionThreePercent = read(properties, KEY_DISTRIBUTION_THREE_PERCENT, preferences.distributionThreePercent);
        preferences.distributionThreeArmor = read(properties, KEY_DISTRIBUTION_THREE_ARMOR, preferences.distributionThreeArmor);
        preferences.distributionThreeWeapon = read(properties, KEY_DISTRIBUTION_THREE_WEAPON, preferences.distributionThreeWeapon);
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
        properties.setProperty(KEY_SUMMON_ARMOR_MATERIAL, summonArmorMaterial);
        properties.setProperty(KEY_SUMMON_TOOL_MATERIAL, summonToolMaterial);
        properties.setProperty(KEY_SUMMON_REACH, summonReach);
        properties.setProperty(KEY_SUMMON_FAKE_HIT, summonFakeHit);
        properties.setProperty(KEY_SUMMON_STAP, summonStap);
        properties.setProperty(KEY_SUMMON_DAMAGE, summonDamage);
        properties.setProperty(KEY_SUMMON_360_FLEX, summon360Flex);
        properties.setProperty(KEY_SUMMON_SELF_HEAL, summonSelfHeal);
        properties.setProperty(KEY_SUMMON_HEALING_ITEMS, summonHealingItems);
        properties.setProperty(KEY_SUMMON_HEAD, summonHead);
        properties.setProperty(KEY_SUMMON_CHEST, summonChest);
        properties.setProperty(KEY_SUMMON_LEGS, summonLegs);
        properties.setProperty(KEY_SUMMON_FEET, summonFeet);
        properties.setProperty(KEY_SUMMON_MAINHAND, summonMainhand);
        properties.setProperty(KEY_SUMMON_OFFHAND, summonOffhand);
        properties.setProperty(KEY_SUMMON_HOTBAR, summonHotbar);
        properties.setProperty(KEY_SUMMON_INVENTORY, summonInventory);
        properties.setProperty(KEY_SUMMON_EFFECT_ID, summonEffectId);
        properties.setProperty(KEY_SUMMON_EFFECT_DURATION, summonEffectDuration);
        properties.setProperty(KEY_SUMMON_EFFECT_AMPLIFIER, summonEffectAmplifier);
        properties.setProperty(KEY_DISTRIBUTION_ONE_PERCENT, distributionOnePercent);
        properties.setProperty(KEY_DISTRIBUTION_ONE_ARMOR, distributionOneArmor);
        properties.setProperty(KEY_DISTRIBUTION_ONE_WEAPON, distributionOneWeapon);
        properties.setProperty(KEY_DISTRIBUTION_TWO_PERCENT, distributionTwoPercent);
        properties.setProperty(KEY_DISTRIBUTION_TWO_ARMOR, distributionTwoArmor);
        properties.setProperty(KEY_DISTRIBUTION_TWO_WEAPON, distributionTwoWeapon);
        properties.setProperty(KEY_DISTRIBUTION_THREE_PERCENT, distributionThreePercent);
        properties.setProperty(KEY_DISTRIBUTION_THREE_ARMOR, distributionThreeArmor);
        properties.setProperty(KEY_DISTRIBUTION_THREE_WEAPON, distributionThreeWeapon);
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

    public String summonArmorMaterial() {
        return summonArmorMaterial;
    }

    public void setSummonArmorMaterial(String summonArmorMaterial) {
        this.summonArmorMaterial = summonArmorMaterial;
    }

    public String summonToolMaterial() {
        return summonToolMaterial;
    }

    public void setSummonToolMaterial(String summonToolMaterial) {
        this.summonToolMaterial = summonToolMaterial;
    }

    public String summonReach() {
        return summonReach;
    }

    public void setSummonReach(String summonReach) {
        this.summonReach = summonReach;
    }

    public String summonFakeHit() {
        return summonFakeHit;
    }

    public void setSummonFakeHit(String summonFakeHit) {
        this.summonFakeHit = summonFakeHit;
    }

    public String summonStap() {
        return summonStap;
    }

    public void setSummonStap(String summonStap) {
        this.summonStap = summonStap;
    }

    public String summonDamage() {
        return summonDamage;
    }

    public void setSummonDamage(String summonDamage) {
        this.summonDamage = summonDamage;
    }

    public String summon360Flex() {
        return summon360Flex;
    }

    public void setSummon360Flex(String summon360Flex) {
        this.summon360Flex = summon360Flex;
    }

    public String summonSelfHeal() {
        return summonSelfHeal;
    }

    public void setSummonSelfHeal(String summonSelfHeal) {
        this.summonSelfHeal = summonSelfHeal;
    }

    public String summonHealingItems() {
        return summonHealingItems;
    }

    public void setSummonHealingItems(String summonHealingItems) {
        this.summonHealingItems = summonHealingItems;
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

    public String summonInventory() {
        return summonInventory;
    }

    public void setSummonInventory(String summonInventory) {
        this.summonInventory = summonInventory;
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

    public String distributionOnePercent() {
        return distributionOnePercent;
    }

    public void setDistributionOnePercent(String distributionOnePercent) {
        this.distributionOnePercent = distributionOnePercent;
    }

    public String distributionOneArmor() {
        return distributionOneArmor;
    }

    public void setDistributionOneArmor(String distributionOneArmor) {
        this.distributionOneArmor = distributionOneArmor;
    }

    public String distributionOneWeapon() {
        return distributionOneWeapon;
    }

    public void setDistributionOneWeapon(String distributionOneWeapon) {
        this.distributionOneWeapon = distributionOneWeapon;
    }

    public String distributionTwoPercent() {
        return distributionTwoPercent;
    }

    public void setDistributionTwoPercent(String distributionTwoPercent) {
        this.distributionTwoPercent = distributionTwoPercent;
    }

    public String distributionTwoArmor() {
        return distributionTwoArmor;
    }

    public void setDistributionTwoArmor(String distributionTwoArmor) {
        this.distributionTwoArmor = distributionTwoArmor;
    }

    public String distributionTwoWeapon() {
        return distributionTwoWeapon;
    }

    public void setDistributionTwoWeapon(String distributionTwoWeapon) {
        this.distributionTwoWeapon = distributionTwoWeapon;
    }

    public String distributionThreePercent() {
        return distributionThreePercent;
    }

    public void setDistributionThreePercent(String distributionThreePercent) {
        this.distributionThreePercent = distributionThreePercent;
    }

    public String distributionThreeArmor() {
        return distributionThreeArmor;
    }

    public void setDistributionThreeArmor(String distributionThreeArmor) {
        this.distributionThreeArmor = distributionThreeArmor;
    }

    public String distributionThreeWeapon() {
        return distributionThreeWeapon;
    }

    public void setDistributionThreeWeapon(String distributionThreeWeapon) {
        this.distributionThreeWeapon = distributionThreeWeapon;
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
