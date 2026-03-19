package com.zahen.playerbatch.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class SelectionWandItem {
    public static final String TAG_KEY = "PlayerBatchSelectionWand";

    private SelectionWandItem() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("PlayerBatch Wand").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_KEY, true);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    public static boolean isSelectionWand(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return customData.copyTag().getBoolean(TAG_KEY).orElse(false);
    }
}

