package com.zahen.playerbatch.core;

import net.minecraft.world.entity.EquipmentSlot;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

public final class BotConfig {
    private final String formation;
    private final BotLoadout loadout;

    public BotConfig(String formation, BotLoadout loadout) {
        this.formation = formation == null || formation.isBlank() ? "circle" : formation;
        this.loadout = loadout == null ? new BotLoadout() : loadout;
    }

    public String formation() {
        return formation;
    }

    public BotLoadout loadout() {
        return loadout;
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
        for (int index = 0; index < loadout.effects().size(); index++) {
            BotLoadout.EffectSpec effect = loadout.effects().get(index);
            properties.setProperty("effect." + index + ".id", effect.effectId());
            properties.setProperty("effect." + index + ".duration", Integer.toString(effect.durationSeconds()));
            properties.setProperty("effect." + index + ".amp", Integer.toString(effect.amplifier()));
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
        return new BotConfig(properties.getProperty("formation", "circle"), loadout);
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
}
