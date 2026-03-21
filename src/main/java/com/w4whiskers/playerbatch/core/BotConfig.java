package com.w4whiskers.playerbatch.core;

import com.w4whiskers.playerbatch.extapi.PlayerBatchSummonPlan;
import net.minecraft.world.entity.EquipmentSlot;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class BotConfig {
    private final String formation;
    private final BotLoadout loadout;
    private final List<DistributionRule> distributions;
    private final CombatPresetSpec combatPreset;
    private final PlayerBatchSummonPlan extensionPlan;

    public BotConfig(String formation, BotLoadout loadout) {
        this(formation, loadout, List.of(), null, new PlayerBatchSummonPlan());
    }

    public BotConfig(String formation, BotLoadout loadout, List<DistributionRule> distributions) {
        this(formation, loadout, distributions, null, new PlayerBatchSummonPlan());
    }

    public BotConfig(String formation, BotLoadout loadout, CombatPresetSpec combatPreset) {
        this(formation, loadout, List.of(), combatPreset, new PlayerBatchSummonPlan());
    }

    public BotConfig(String formation, BotLoadout loadout, List<DistributionRule> distributions, CombatPresetSpec combatPreset) {
        this(formation, loadout, distributions, combatPreset, new PlayerBatchSummonPlan());
    }

    public BotConfig(String formation, BotLoadout loadout, CombatPresetSpec combatPreset, PlayerBatchSummonPlan extensionPlan) {
        this(formation, loadout, List.of(), combatPreset, extensionPlan);
    }

    public BotConfig(String formation, BotLoadout loadout, List<DistributionRule> distributions, CombatPresetSpec combatPreset, PlayerBatchSummonPlan extensionPlan) {
        this.formation = formation == null || formation.isBlank() ? "circle" : formation;
        this.loadout = loadout == null ? new BotLoadout() : loadout;
        this.distributions = distributions == null ? List.of() : List.copyOf(distributions);
        this.combatPreset = combatPreset;
        this.extensionPlan = extensionPlan == null ? new PlayerBatchSummonPlan() : extensionPlan.copy();
    }

    public String formation() {
        return formation;
    }

    public BotLoadout loadout() {
        return loadout;
    }

    public List<DistributionRule> distributions() {
        return distributions;
    }

    public CombatPresetSpec combatPreset() {
        return combatPreset;
    }

    public PlayerBatchSummonPlan extensionPlan() {
        return extensionPlan.copy();
    }

    public String encode() {
        Properties properties = new Properties();
        properties.setProperty("formation", formation);
        writeSlot(properties, "head", EquipmentSlot.HEAD);
        writeSlot(properties, "chest", EquipmentSlot.CHEST);
        writeSlot(properties, "legs", EquipmentSlot.LEGS);
        writeSlot(properties, "feet", EquipmentSlot.FEET);
        writeSlot(properties, "mainhand", EquipmentSlot.MAINHAND);
        writeSlot(properties, "offhand", EquipmentSlot.OFFHAND);
        for (int index = 0; index < 9; index++) {
            BotLoadout.StackSpec spec = loadout.hotbar().get(index);
            if (spec != null) {
                properties.setProperty("hotbar." + index + ".item", spec.itemId());
                properties.setProperty("hotbar." + index + ".count", Integer.toString(spec.count()));
            }
        }
        for (int index = 9; index < 36; index++) {
            BotLoadout.StackSpec spec = loadout.inventory().get(index);
            if (spec != null) {
                properties.setProperty("inventory." + index + ".item", spec.itemId());
                properties.setProperty("inventory." + index + ".count", Integer.toString(spec.count()));
            }
        }
        for (int index = 0; index < loadout.effects().size(); index++) {
            BotLoadout.EffectSpec effect = loadout.effects().get(index);
            properties.setProperty("effect." + index + ".id", effect.effectId());
            properties.setProperty("effect." + index + ".duration", Integer.toString(effect.durationSeconds()));
            properties.setProperty("effect." + index + ".amp", Integer.toString(effect.amplifier()));
        }
        properties.setProperty("distribution.count", Integer.toString(distributions.size()));
        for (int index = 0; index < distributions.size(); index++) {
            DistributionRule rule = distributions.get(index);
            properties.setProperty("distribution." + index + ".percent", Integer.toString(rule.percent()));
            rule.loadout().writeTo(properties, "distribution." + index + ".");
        }
        if (combatPreset != null) {
            properties.setProperty("combatPreset.enabled", "true");
            properties.setProperty("combatPreset.offhandMode", combatPreset.offhandMode().name());
            properties.setProperty("combatPreset.offhandCount", Integer.toString(combatPreset.offhandCount()));
            properties.setProperty("combatPreset.selfHealEnabled", Boolean.toString(combatPreset.selfHealEnabled()));
            properties.setProperty("combatPreset.healingItemsEnabled", Boolean.toString(combatPreset.healingItemsEnabled()));
            properties.setProperty("combatPreset.reach", Integer.toString(combatPreset.reach()));
            properties.setProperty("combatPreset.fakeHitEnabled", Boolean.toString(combatPreset.fakeHitEnabled()));
            properties.setProperty("combatPreset.stapEnabled", Boolean.toString(combatPreset.stapEnabled()));
            properties.setProperty("combatPreset.damageEnabled", Boolean.toString(combatPreset.damageEnabled()));
            properties.setProperty("combatPreset.flex360Enabled", Boolean.toString(combatPreset.flex360Enabled()));
        }
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "PlayerBatch summon config");
            return writer.toString();
        } catch (IOException ignored) {
            return "";
        }
    }

    private void writeSlot(Properties properties, String key, EquipmentSlot slot) {
        BotLoadout.StackSpec spec = loadout.equipment().get(slot);
        if (spec != null) {
            properties.setProperty(key + ".item", spec.itemId());
            properties.setProperty(key + ".count", Integer.toString(spec.count()));
        }
    }

    public static BotConfig decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(encoded));
        } catch (IOException ignored) {
            return empty();
        }
        BotLoadout loadout = new BotLoadout();
        readSlot(properties, "head", EquipmentSlot.HEAD, loadout);
        readSlot(properties, "chest", EquipmentSlot.CHEST, loadout);
        readSlot(properties, "legs", EquipmentSlot.LEGS, loadout);
        readSlot(properties, "feet", EquipmentSlot.FEET, loadout);
        readSlot(properties, "mainhand", EquipmentSlot.MAINHAND, loadout);
        readSlot(properties, "offhand", EquipmentSlot.OFFHAND, loadout);
        for (int index = 0; index < 9; index++) {
            String itemId = properties.getProperty("hotbar." + index + ".item", "").trim();
            if (!itemId.isEmpty()) {
                loadout.hotbar().put(index, new BotLoadout.StackSpec(itemId, parseInt(properties.getProperty("hotbar." + index + ".count"), 1)));
            }
        }
        for (int index = 9; index < 36; index++) {
            String itemId = properties.getProperty("inventory." + index + ".item", "").trim();
            if (!itemId.isEmpty()) {
                loadout.inventory().put(index, new BotLoadout.StackSpec(itemId, parseInt(properties.getProperty("inventory." + index + ".count"), 1)));
            }
        }
        for (int index = 0; index < 12; index++) {
            String effectId = properties.getProperty("effect." + index + ".id", "").trim();
            if (!effectId.isEmpty()) {
                loadout.effects().add(new BotLoadout.EffectSpec(
                        effectId,
                        parseInt(properties.getProperty("effect." + index + ".duration"), 30),
                        parseInt(properties.getProperty("effect." + index + ".amp"), 0)
                ));
            }
        }
        List<DistributionRule> distributions = new ArrayList<>();
        int distributionCount = parseInt(properties.getProperty("distribution.count"), 0);
        for (int index = 0; index < distributionCount; index++) {
            int percent = parseInt(properties.getProperty("distribution." + index + ".percent"), 0);
            BotLoadout distributionLoadout = BotLoadout.readFrom(properties, "distribution." + index + ".");
            if (percent > 0 && !distributionLoadout.isEmpty()) {
                distributions.add(new DistributionRule(percent, distributionLoadout));
            }
        }
        CombatPresetSpec combatPreset = null;
        if (Boolean.parseBoolean(properties.getProperty("combatPreset.enabled", "false"))) {
            CombatPresetSpec.OffhandMode offhandMode;
            try {
                offhandMode = CombatPresetSpec.OffhandMode.valueOf(properties.getProperty("combatPreset.offhandMode", "SHIELD"));
            } catch (IllegalArgumentException ignored) {
                offhandMode = CombatPresetSpec.OffhandMode.SHIELD;
            }
            combatPreset = new CombatPresetSpec(
                    CombatPresetSpec.ArmorTier.NONE,
                    CombatPresetSpec.ToolTier.NONE,
                    offhandMode,
                    parseInt(properties.getProperty("combatPreset.offhandCount"), 1),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.selfHealEnabled", "false")),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.healingItemsEnabled", "false")),
                    List.of(),
                    parseInt(properties.getProperty("combatPreset.reach"), 3),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.fakeHitEnabled", "true")),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.stapEnabled", "false")),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.damageEnabled", "true")),
                    Boolean.parseBoolean(properties.getProperty("combatPreset.flex360Enabled", "false"))
            );
        }
        return new BotConfig(properties.getProperty("formation", "circle"), loadout, distributions, combatPreset, new PlayerBatchSummonPlan());
    }

    private static void readSlot(Properties properties, String key, EquipmentSlot slot, BotLoadout loadout) {
        String itemId = properties.getProperty(key + ".item", "").trim();
        if (!itemId.isEmpty()) {
            loadout.equipment().put(slot, new BotLoadout.StackSpec(itemId, parseInt(properties.getProperty(key + ".count"), 1)));
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static BotConfig empty() {
        return new BotConfig("circle", new BotLoadout());
    }

    public record DistributionRule(int percent, BotLoadout loadout) {
        public DistributionRule {
            percent = Math.max(0, Math.min(100, percent));
            loadout = loadout == null ? new BotLoadout() : loadout;
        }
    }
}
