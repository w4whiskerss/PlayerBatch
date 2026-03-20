package com.zahen.playerbatch.core;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BotLoadout {
    private final Map<EquipmentSlot, StackSpec> equipment = new EnumMap<>(EquipmentSlot.class);
    private final Map<Integer, StackSpec> hotbar = new LinkedHashMap<>();
    private final List<EffectSpec> effects = new ArrayList<>();

    public Map<EquipmentSlot, StackSpec> equipment() {
        return equipment;
    }

    public Map<Integer, StackSpec> hotbar() {
        return hotbar;
    }

    public List<EffectSpec> effects() {
        return effects;
    }

    public boolean isEmpty() {
        return equipment.isEmpty() && hotbar.isEmpty() && effects.isEmpty();
    }

    public void applyTo(EntityPlayerMPFake fakePlayer) {
        for (Map.Entry<EquipmentSlot, StackSpec> entry : equipment.entrySet()) {
            Item item = resolveItem(entry.getValue().itemId());
            if (item != null) {
                fakePlayer.setItemSlot(entry.getKey(), new ItemStack(item, Math.max(1, entry.getValue().count())));
            }
        }
        for (Map.Entry<Integer, StackSpec> entry : hotbar.entrySet()) {
            Item item = resolveItem(entry.getValue().itemId());
            if (item != null && entry.getKey() >= 0 && entry.getKey() < fakePlayer.getInventory().getContainerSize()) {
                fakePlayer.getInventory().setItem(entry.getKey(), new ItemStack(item, Math.max(1, entry.getValue().count())));
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

    public record StackSpec(String itemId, int count) {
    }

    public record EffectSpec(String effectId, int durationSeconds, int amplifier) {
    }
}
