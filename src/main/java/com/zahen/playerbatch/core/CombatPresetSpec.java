package com.zahen.playerbatch.core;

import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record CombatPresetSpec(
        ArmorTier armorTier,
        ToolTier toolTier,
        OffhandMode offhandMode,
        int offhandCount,
        boolean selfHealEnabled,
        boolean healingItemsEnabled,
        List<BotLoadout.StackSpec> healingItems,
        int reach,
        boolean fakeHitEnabled,
        boolean stapEnabled,
        boolean damageEnabled,
        boolean flex360Enabled
) {
    public CombatPresetSpec {
        armorTier = armorTier == null ? ArmorTier.NONE : armorTier;
        toolTier = toolTier == null ? ToolTier.NONE : toolTier;
        offhandMode = offhandMode == null ? OffhandMode.SHIELD : offhandMode;
        offhandCount = Math.max(0, offhandCount);
        healingItems = healingItems == null ? List.of() : List.copyOf(healingItems);
        reach = Math.max(1, Math.min(10, reach));
        if (!selfHealEnabled) {
            healingItemsEnabled = false;
            healingItems = List.of();
        }
    }

    public BotLoadout createLoadout() {
        BotLoadout loadout = new BotLoadout();
        addArmor(loadout);
        addTools(loadout);
        addOffhand(loadout);
        addHealingItems(loadout);
        return loadout;
    }

    private void addArmor(BotLoadout loadout) {
        if (armorTier == ArmorTier.NONE) {
            return;
        }
        String material = armorTier.materialKey();
        loadout.equipment().put(EquipmentSlot.HEAD, new BotLoadout.StackSpec("minecraft:" + material + "_helmet", 1));
        loadout.equipment().put(EquipmentSlot.CHEST, new BotLoadout.StackSpec("minecraft:" + material + "_chestplate", 1));
        loadout.equipment().put(EquipmentSlot.LEGS, new BotLoadout.StackSpec("minecraft:" + material + "_leggings", 1));
        loadout.equipment().put(EquipmentSlot.FEET, new BotLoadout.StackSpec("minecraft:" + material + "_boots", 1));
    }

    private void addTools(BotLoadout loadout) {
        if (toolTier == ToolTier.NONE) {
            return;
        }
        String material = toolTier.materialKey();
        loadout.equipment().put(EquipmentSlot.MAINHAND, new BotLoadout.StackSpec("minecraft:" + material + "_sword", 1));
        loadout.hotbar().put(1, new BotLoadout.StackSpec("minecraft:" + material + "_axe", 1));
    }

    private void addOffhand(BotLoadout loadout) {
        if (offhandMode == OffhandMode.TOTEM && offhandCount > 0) {
            loadout.equipment().put(EquipmentSlot.OFFHAND, new BotLoadout.StackSpec("minecraft:totem_of_undying", 1));
            int remainingTotems = Math.max(0, offhandCount - 1);
            int nextSlot = 9;
            for (int index = 0; index < remainingTotems && nextSlot < 36; index++, nextSlot++) {
                loadout.inventory().put(nextSlot, new BotLoadout.StackSpec("minecraft:totem_of_undying", 1));
            }
            if (nextSlot < 36) {
                loadout.inventory().put(nextSlot, new BotLoadout.StackSpec("minecraft:shield", 1));
            }
            return;
        }
        loadout.equipment().put(EquipmentSlot.OFFHAND, new BotLoadout.StackSpec("minecraft:shield", 1));
    }

    private void addHealingItems(BotLoadout loadout) {
        if (!healingItemsEnabled || healingItems.isEmpty()) {
            return;
        }
        int nextSlot = nextFreeSlot(loadout);
        for (BotLoadout.StackSpec healingItem : healingItems) {
            if (nextSlot >= 36) {
                break;
            }
            loadout.inventory().put(nextSlot, healingItem);
            nextSlot++;
        }
    }

    private int nextFreeSlot(BotLoadout loadout) {
        for (int slot = 9; slot < 36; slot++) {
            if (!loadout.inventory().containsKey(slot)) {
                return slot;
            }
        }
        return 36;
    }

    public enum ArmorTier {
        NONE(""),
        IRON("iron"),
        GOLDEN("golden"),
        DIAMOND("diamond"),
        NETHERITE("netherite");

        private final String materialKey;

        ArmorTier(String materialKey) {
            this.materialKey = materialKey;
        }

        public String materialKey() {
            return materialKey;
        }

        public static ArmorTier fromOptionToken(String token) {
            return switch (token.toLowerCase(Locale.ROOT)) {
                case "ironarmor" -> IRON;
                case "goldenarmor" -> GOLDEN;
                case "diamondarmor" -> DIAMOND;
                case "netheritearmor" -> NETHERITE;
                default -> NONE;
            };
        }
    }

    public enum ToolTier {
        NONE(""),
        IRON("iron"),
        DIAMOND("diamond"),
        NETHERITE("netherite");

        private final String materialKey;

        ToolTier(String materialKey) {
            this.materialKey = materialKey;
        }

        public String materialKey() {
            return materialKey;
        }

        public static ToolTier fromOptionToken(String token) {
            return switch (token.toLowerCase(Locale.ROOT)) {
                case "irontools" -> IRON;
                case "diamondtools" -> DIAMOND;
                case "netheritetools" -> NETHERITE;
                default -> NONE;
            };
        }
    }

    public enum OffhandMode {
        SHIELD,
        TOTEM
    }

    public record SavedCombatPreset(String name, int count, String rawOptions) {
    }
}
