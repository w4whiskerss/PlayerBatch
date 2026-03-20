package com.zahen.playerbatch.core;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class BotLoadout {
    private final Map<EquipmentSlot, StackSpec> equipment = new EnumMap<>(EquipmentSlot.class);
    private final Map<Integer, StackSpec> hotbar = new LinkedHashMap<>();
    private final Map<Integer, StackSpec> inventory = new LinkedHashMap<>();
    private final List<EffectSpec> effects = new ArrayList<>();

    public Map<EquipmentSlot, StackSpec> equipment() {
        return equipment;
    }

    public Map<Integer, StackSpec> hotbar() {
        return hotbar;
    }

    public Map<Integer, StackSpec> inventory() {
        return inventory;
    }

    public List<EffectSpec> effects() {
        return effects;
    }

    public boolean isEmpty() {
        return equipment.isEmpty() && hotbar.isEmpty() && inventory.isEmpty() && effects.isEmpty();
    }

    public BotLoadout copy() {
        BotLoadout copy = new BotLoadout();
        copy.equipment.putAll(equipment);
        copy.hotbar.putAll(hotbar);
        copy.inventory.putAll(inventory);
        copy.effects.addAll(effects);
        return copy;
    }

    public BotLoadout mergedWith(BotLoadout overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return copy();
        }
        BotLoadout merged = copy();
        merged.equipment.putAll(overrides.equipment);
        merged.hotbar.putAll(overrides.hotbar);
        merged.inventory.putAll(overrides.inventory);
        merged.effects.addAll(overrides.effects);
        return merged;
    }

    public void writeTo(Properties properties, String prefix) {
        for (Map.Entry<EquipmentSlot, StackSpec> entry : equipment.entrySet()) {
            String key = switch (entry.getKey()) {
                case HEAD -> "head";
                case CHEST -> "chest";
                case LEGS -> "legs";
                case FEET -> "feet";
                case MAINHAND -> "mainhand";
                case OFFHAND -> "offhand";
                default -> null;
            };
            if (key != null) {
                properties.setProperty(prefix + key + ".item", entry.getValue().itemId());
                properties.setProperty(prefix + key + ".count", Integer.toString(entry.getValue().count()));
            }
        }
        for (Map.Entry<Integer, StackSpec> entry : hotbar.entrySet()) {
            properties.setProperty(prefix + "hotbar." + entry.getKey() + ".item", entry.getValue().itemId());
            properties.setProperty(prefix + "hotbar." + entry.getKey() + ".count", Integer.toString(entry.getValue().count()));
        }
        for (Map.Entry<Integer, StackSpec> entry : inventory.entrySet()) {
            properties.setProperty(prefix + "inventory." + entry.getKey() + ".item", entry.getValue().itemId());
            properties.setProperty(prefix + "inventory." + entry.getKey() + ".count", Integer.toString(entry.getValue().count()));
        }
        for (int index = 0; index < effects.size(); index++) {
            EffectSpec effect = effects.get(index);
            properties.setProperty(prefix + "effect." + index + ".id", effect.effectId());
            properties.setProperty(prefix + "effect." + index + ".duration", Integer.toString(effect.durationSeconds()));
            properties.setProperty(prefix + "effect." + index + ".amp", Integer.toString(effect.amplifier()));
        }
    }

    public static BotLoadout readFrom(Properties properties, String prefix) {
        BotLoadout loadout = new BotLoadout();
        readSlot(properties, prefix, "head", EquipmentSlot.HEAD, loadout);
        readSlot(properties, prefix, "chest", EquipmentSlot.CHEST, loadout);
        readSlot(properties, prefix, "legs", EquipmentSlot.LEGS, loadout);
        readSlot(properties, prefix, "feet", EquipmentSlot.FEET, loadout);
        readSlot(properties, prefix, "mainhand", EquipmentSlot.MAINHAND, loadout);
        readSlot(properties, prefix, "offhand", EquipmentSlot.OFFHAND, loadout);
        for (int index = 0; index < 9; index++) {
            String itemId = properties.getProperty(prefix + "hotbar." + index + ".item", "").trim();
            if (!itemId.isEmpty()) {
                loadout.hotbar.put(index, new StackSpec(itemId, parseInt(properties.getProperty(prefix + "hotbar." + index + ".count"), 1)));
            }
        }
        for (int index = 9; index < 36; index++) {
            String itemId = properties.getProperty(prefix + "inventory." + index + ".item", "").trim();
            if (!itemId.isEmpty()) {
                loadout.inventory.put(index, new StackSpec(itemId, parseInt(properties.getProperty(prefix + "inventory." + index + ".count"), 1)));
            }
        }
        for (int index = 0; index < 12; index++) {
            String effectId = properties.getProperty(prefix + "effect." + index + ".id", "").trim();
            if (!effectId.isEmpty()) {
                loadout.effects.add(new EffectSpec(
                        effectId,
                        parseInt(properties.getProperty(prefix + "effect." + index + ".duration"), 30),
                        parseInt(properties.getProperty(prefix + "effect." + index + ".amp"), 0)
                ));
            }
        }
        return loadout;
    }

    private static void readSlot(Properties properties, String prefix, String key, EquipmentSlot slot, BotLoadout loadout) {
        String itemId = properties.getProperty(prefix + key + ".item", "").trim();
        if (!itemId.isEmpty()) {
            loadout.equipment.put(slot, new StackSpec(itemId, parseInt(properties.getProperty(prefix + key + ".count"), 1)));
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public void applyTo(EntityPlayerMPFake fakePlayer) {
        for (Map.Entry<EquipmentSlot, StackSpec> entry : equipment.entrySet()) {
            ItemStack stack = createStack(entry.getValue());
            if (!stack.isEmpty()) {
                fakePlayer.setItemSlot(entry.getKey(), stack);
            }
        }
        for (Map.Entry<Integer, StackSpec> entry : hotbar.entrySet()) {
            ItemStack stack = createStack(entry.getValue());
            if (!stack.isEmpty() && entry.getKey() >= 0 && entry.getKey() < fakePlayer.getInventory().getContainerSize()) {
                fakePlayer.getInventory().setItem(entry.getKey(), stack);
            }
        }
        for (Map.Entry<Integer, StackSpec> entry : inventory.entrySet()) {
            ItemStack stack = createStack(entry.getValue());
            if (!stack.isEmpty() && entry.getKey() >= 9 && entry.getKey() < fakePlayer.getInventory().getContainerSize()) {
                fakePlayer.getInventory().setItem(entry.getKey(), stack);
            }
        }
        for (EffectSpec effectSpec : effects) {
            Holder<MobEffect> holder = resolveEffect(effectSpec.effectId());
            if (holder != null) {
                fakePlayer.addEffect(new MobEffectInstance(holder, Math.max(1, effectSpec.durationSeconds()) * 20, Math.max(0, effectSpec.amplifier())));
            }
        }
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalizedId = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        return BuiltInRegistries.ITEM.keySet().stream()
                .filter(key -> key.toString().equals(normalizedId))
                .findFirst()
                .flatMap(key -> BuiltInRegistries.ITEM.get(key).map(Holder.Reference::value))
                .orElse(null);
    }

    private static Holder<MobEffect> resolveEffect(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return null;
        }
        String normalizedId = effectId.contains(":") ? effectId : "minecraft:" + effectId;
        return BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .filter(key -> key.toString().equals(normalizedId))
                .findFirst()
                .flatMap(BuiltInRegistries.MOB_EFFECT::get)
                .map(holder -> (Holder<MobEffect>) holder)
                .orElse(null);
    }

    private static ItemStack createStack(StackSpec spec) {
        if (spec == null || spec.itemId() == null || spec.itemId().isBlank()) {
            return ItemStack.EMPTY;
        }
        String itemId = spec.itemId();
        int count = Math.max(1, spec.count());
        return switch (itemId) {
            case "minecraft:potion_of_healing" -> potionStack(Items.POTION, Potions.HEALING, count);
            case "minecraft:splash_potion_of_healing" -> potionStack(Items.SPLASH_POTION, Potions.HEALING, count);
            case "minecraft:lingering_potion_of_healing" -> potionStack(Items.LINGERING_POTION, Potions.HEALING, count);
            case "minecraft:potion_of_regeneration" -> potionStack(Items.POTION, Potions.REGENERATION, count);
            case "minecraft:splash_potion_of_regeneration" -> potionStack(Items.SPLASH_POTION, Potions.REGENERATION, count);
            case "minecraft:lingering_potion_of_regeneration" -> potionStack(Items.LINGERING_POTION, Potions.REGENERATION, count);
            default -> {
                Item item = resolveItem(itemId);
                yield item == null ? ItemStack.EMPTY : new ItemStack(item, count);
            }
        };
    }

    private static ItemStack potionStack(Item item, Holder<net.minecraft.world.item.alchemy.Potion> potion, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    public record StackSpec(String itemId, int count) {
    }

    public record EffectSpec(String effectId, int durationSeconds, int amplifier) {
    }
}
