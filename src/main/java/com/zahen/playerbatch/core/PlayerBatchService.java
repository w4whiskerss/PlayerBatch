package com.zahen.playerbatch.core;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import com.zahen.playerbatch.PlayerBatch;
import com.zahen.playerbatch.command.CombatPresetParser;
import com.zahen.playerbatch.compat.CommandCompat;
import com.zahen.playerbatch.config.CombatPresetStore;
import com.zahen.playerbatch.config.KitStore;
import com.zahen.playerbatch.config.PlayerBatchConfig;
import com.zahen.playerbatch.item.SelectionWandItem;
import com.zahen.playerbatch.name.NamePlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerBatchService {
    private static final String BOT_TAG = "bot";
    private static final int AI_TICK_INTERVAL = 1;
    private static final int FLEX_SPIN_STEPS = 8;
    private static final String DEFAULT_FORMATION = "circle";
    private static final ConcurrentMap<MinecraftServer, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final MobEffectInstance SELECTED_GLOWING = new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false);

    private PlayerBatchService() {
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames) {
        return requestSummon(source, count, rawNames, BotConfig.empty());
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames, String rawFormation) {
        return requestSummon(source, count, rawNames, new BotConfig(rawFormation, new BotLoadout()));
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames, BotConfig config) {
        if (source.getServer() == null) {
            return 0;
        }

        String formation = normalizeFormation(config.formation());
        if (formation == null) {
            source.sendFailure(Component.literal("Unknown formation. Use: circle, square, triangle, random, single block."));
            return 0;
        }

        int maxCount = PlayerBatchConfig.getMaxSummonCount();
        if (count < 1 || count > maxCount) {
            source.sendFailure(Component.literal("Count must be between 1 and " + maxCount + "."));
            return 0;
        }

        List<String> preferredNames;
        try {
            preferredNames = NamePlanner.parseRequestedNames(rawNames);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Preparing " + count + " bot names...").withStyle(ChatFormatting.YELLOW),
                false
        );

        NamePlanner.planNamesAsync(count, preferredNames).whenComplete((plannedNames, throwable) -> source.getServer().execute(() -> {
            if (throwable != null) {
                PlayerBatch.LOGGER.error("Failed to plan names for {} bots", count, throwable);
                source.sendFailure(Component.literal("Failed to fetch names. Check the log for details."));
                return;
            }

            int queued = state(source.getServer()).queueBatch(source, plannedNames, new BotConfig(formation, config.loadout(), config.distributions(), config.combatPreset()));
            if (queued <= 0) {
                source.sendFailure(Component.literal("No valid bot names were available to queue."));
                return;
            }

            source.sendSuccess(
                    () -> Component.literal("Queued " + queued + " bot" + (queued == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GREEN),
                    true
            );
        }));

        return 1;
    }

    public static int setLimit(CommandSourceStack source, int value) {
        int sanitized = PlayerBatchConfig.setMaxSummonCount(value);
        broadcastState(source.getServer());
        source.sendSuccess(() -> Component.literal("Player batch limit set to " + sanitized + "."), true);
        return 1;
    }

    public static int setSpawnsPerTick(CommandSourceStack source, int value) {
        int sanitized = PlayerBatchConfig.setMaxSpawnsPerTick(value);
        broadcastState(source.getServer());
        source.sendSuccess(() -> Component.literal("Max spawns per tick set to " + sanitized + "."), true);
        return 1;
    }

    public static int setDebug(CommandSourceStack source, boolean enabled) {
        PlayerBatchConfig.setDebugEnabled(enabled);
        broadcastState(source.getServer());
        source.sendSuccess(() -> Component.literal("PlayerBatch debug mode " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    public static int giveSelectionWand(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can receive the selection wand."));
            return 0;
        }

        ItemStack wand = SelectionWandItem.create();
        boolean added = player.getInventory().add(wand);
        if (!added) {
            player.drop(wand, false);
        }

        source.sendSuccess(() -> Component.literal("Selection wand granted. Right-click fake players to toggle selection."), false);
        return 1;
    }

    public static int clearSelection(CommandSourceStack source) {
        int cleared = state(source.getServer()).clearSelection();
        source.sendSuccess(() -> Component.literal("Cleared " + cleared + " selected bot" + (cleared == 1 ? "" : "s") + "."), true);
        return 1;
    }

    public static int listSelection(CommandSourceStack source) {
        List<String> selectedNames = state(source.getServer()).selectedNames();
        if (selectedNames.isEmpty()) {
            source.sendFailure(Component.literal("No fake players are selected."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Selected bots (" + selectedNames.size() + "): " + String.join(", ", selectedNames)),
                false
        );
        return selectedNames.size();
    }

    public static int selectAll(CommandSourceStack source) {
        int selected = state(source.getServer()).selectAll();
        source.sendSuccess(() -> Component.literal("Selected " + selected + " fake bot" + (selected == 1 ? "" : "s") + "."), true);
        return selected;
    }

    public static int selectWithinRange(CommandSourceStack source, double range) {
        int selected = state(source.getServer()).selectWithinRange(source.getPosition(), range);
        if (selected <= 0) {
            source.sendFailure(Component.literal("No fake players found within range " + range + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Selected " + selected + " fake bot" + (selected == 1 ? "" : "s") + " within range " + (int) range + "."), true);
        return selected;
    }

    public static int selectClosest(CommandSourceStack source, int count) {
        int selected = state(source.getServer()).selectClosest(source.getPosition(), count);
        if (selected <= 0) {
            source.sendFailure(Component.literal("No fake players found to select."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Selected the closest " + selected + " fake bot" + (selected == 1 ? "" : "s") + "."), true);
        return selected;
    }

    public static int runSelectedAction(CommandSourceStack source, String action) {
        int affected = state(source.getServer()).runAction(source, action);
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected fake players matched that action."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Ran action on " + affected + " selected bot" + (affected == 1 ? "" : "s") + "."), true);
        return affected;
    }

    public static int runSelectedActionSet(CommandSourceStack source, String rawActions) {
        if (rawActions == null || rawActions.isBlank()) {
            source.sendFailure(Component.literal("No action set was provided."));
            return 0;
        }
        int bestAffected = 0;
        int commandsRun = 0;
        for (String action : rawActions.split("\\R+")) {
            String trimmed = action.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int affected = state(source.getServer()).runAction(source, trimmed);
            if (affected > 0) {
                bestAffected = Math.max(bestAffected, affected);
                commandsRun++;
            }
        }
        if (commandsRun <= 0) {
            source.sendFailure(Component.literal("No selected fake players matched that action set."));
            return 0;
        }
        int finalBestAffected = bestAffected;
        int finalCommandsRun = commandsRun;
        source.sendSuccess(() -> Component.literal("Ran " + finalCommandsRun + " action command" + suffix(finalCommandsRun) + " across " + finalBestAffected + " selected bot" + suffix(finalBestAffected) + "."), true);
        return finalBestAffected;
    }

    public static int teleportSelection(CommandSourceStack source, String directionName, String blockName) {
        Direction direction = parseDirection(directionName);
        if (direction == null) {
            source.sendFailure(Component.literal("Direction must be one of: up, below, north, south, east, west."));
            return 0;
        }

        if (!isKnownBlock(blockName)) {
            source.sendFailure(Component.literal("Unknown block: " + blockName));
            return 0;
        }

        int moved = state(source.getServer()).teleportSelection(source, direction, blockName);
        if (moved <= 0) {
            source.sendFailure(Component.literal("No selected fake players were teleported to nearby " + blockName + " blocks."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Teleported " + moved + " selected bot" + (moved == 1 ? "" : "s") + " near " + blockName + "."), true);
        return moved;
    }

    public static int applySelectedItem(CommandSourceStack source, String rawSlot, String rawItem, int count) {
        EquipmentSlot slot = parseEquipmentSlot(rawSlot);
        Item item = parseItem(rawItem);
        if (slot == null) {
            source.sendFailure(Component.literal("Unknown slot. Use: head, chest, legs, feet, mainhand, offhand."));
            return 0;
        }
        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + rawItem));
            return 0;
        }
        int affected = state(source.getServer()).applySelectedItem(slot, item, count);
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected managed bots were available to edit."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Applied item to " + affected + " selected bot" + suffix(affected) + "."), true);
        return affected;
    }

    public static int applySelectedHotbarSlot(CommandSourceStack source, int slotIndex, String rawItem, int count) {
        Item item = parseItem(rawItem);
        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + rawItem));
            return 0;
        }
        if (slotIndex < 0 || slotIndex > 8) {
            source.sendFailure(Component.literal("Hotbar slot must be between 1 and 9."));
            return 0;
        }
        int affected = state(source.getServer()).applySelectedHotbarSlot(slotIndex, item, count);
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected managed bots were available to edit."));
            return 0;
        }
        int displaySlot = slotIndex + 1;
        source.sendSuccess(() -> Component.literal("Applied item to hotbar slot " + displaySlot + " for " + affected + " selected bot" + suffix(affected) + "."), true);
        return affected;
    }

    public static int applySelectedEffect(CommandSourceStack source, String rawEffect, int durationSeconds, int amplifier) {
        var effectHolder = parseEffect(rawEffect);
        if (effectHolder == null) {
            source.sendFailure(Component.literal("Unknown effect: " + rawEffect));
            return 0;
        }
        int affected = state(source.getServer()).applySelectedEffect(effectHolder, durationSeconds, amplifier);
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected managed bots were available to edit."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Applied effect to " + affected + " selected bot" + suffix(affected) + "."), true);
        return affected;
    }

    public static int clearSelectedEffects(CommandSourceStack source) {
        int affected = state(source.getServer()).clearSelectedEffects();
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected managed bots were available to edit."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared effects from " + affected + " selected bot" + suffix(affected) + "."), true);
        return affected;
    }

    public static int createGroup(CommandSourceStack source, String rawName) {
        GroupResult result = state(source.getServer()).createGroup(rawName);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    public static int assignSelectionToGroup(CommandSourceStack source, String rawName) {
        GroupResult result = state(source.getServer()).assignSelectionToGroup(rawName);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int removeSelectionFromGroup(CommandSourceStack source, String rawName) {
        GroupResult result = state(source.getServer()).removeSelectionFromGroup(rawName);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int listGroups(CommandSourceStack source) {
        List<String> groups = state(source.getServer()).groupSummaries();
        if (groups.isEmpty()) {
            source.sendFailure(Component.literal("No bot groups exist yet."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Groups: " + String.join(" | ", groups)), false);
        return groups.size();
    }

    public static int setSelectedAiMode(CommandSourceStack source, String rawMode) {
        EnumSet<BotAiMode> modes = BotAiMode.parseSet(rawMode);
        if (modes == null) {
            source.sendFailure(Component.literal("Unknown AI mode. Use: idle, combat, patrol, guard, follow, flee. Combine with +."));
            return 0;
        }
        AiResult result = state(source.getServer()).setSelectedAiModes(modes);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int setAllAiMode(CommandSourceStack source, String rawMode) {
        EnumSet<BotAiMode> modes = BotAiMode.parseSet(rawMode);
        if (modes == null) {
            source.sendFailure(Component.literal("Unknown AI mode. Use: idle, combat, patrol, guard, follow, flee. Combine with +."));
            return 0;
        }
        AiResult result = state(source.getServer()).setAllAiModes(modes);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int summonCombatPreset(CommandSourceStack source, int count, String rawOptions) {
        try {
            CombatPresetParser.validate(rawOptions);
            CombatPresetSpec preset = CombatPresetParser.parse(rawOptions);
            return requestSummon(source, count, "", new BotConfig("circle", preset.createLoadout(), preset));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    public static int saveCombatPreset(CommandSourceStack source, String name, int count, String rawOptions) {
        try {
            String validatedOptions = CombatPresetParser.validate(rawOptions);
            CombatPresetStore.save(name, count, validatedOptions);
            source.sendSuccess(() -> Component.literal("Saved combat preset '" + name + "'."), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    public static int saveKit(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can save kits."));
            return 0;
        }

        BotLoadout loadout = BotLoadout.captureFromPlayer(player);
        if (loadout.isEmpty()) {
            source.sendFailure(Component.literal("Your inventory is empty, so there is nothing to save."));
            return 0;
        }

        KitStore.save(name, loadout);
        source.sendSuccess(() -> Component.literal("Saved kit '" + name + "' from your current inventory."), true);
        return 1;
    }

    public static int loadKit(CommandSourceStack source, String name) {
        BotLoadout loadout = KitStore.get(name);
        if (loadout == null || loadout.isEmpty()) {
            source.sendFailure(Component.literal("Unknown or empty kit: " + name));
            return 0;
        }

        int affected = state(source.getServer()).applySelectedLoadout(loadout);
        if (affected <= 0) {
            source.sendFailure(Component.literal("Select one or more managed bots before loading a kit."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Loaded kit '" + name + "' onto " + affected + " selected bot" + suffix(affected) + "."), true);
        return affected;
    }

    public static int summonSavedCombatPreset(CommandSourceStack source, String name, Integer overrideCount) {
        CombatPresetSpec.SavedCombatPreset preset = CombatPresetStore.get(name);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown combat preset: " + name));
            return 0;
        }
        int count = overrideCount == null ? preset.count() : overrideCount;
        return summonCombatPreset(source, count, preset.rawOptions());
    }

    public static int listCombatPresets(CommandSourceStack source) {
        List<String> names = CombatPresetStore.names();
        if (names.isEmpty()) {
            source.sendFailure(Component.literal("No saved combat presets yet."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Combat presets: " + String.join(", ", names)), false);
        return names.size();
    }

    public static int setGroupAiMode(CommandSourceStack source, String rawName, String rawMode) {
        EnumSet<BotAiMode> modes = BotAiMode.parseSet(rawMode);
        if (modes == null) {
            source.sendFailure(Component.literal("Unknown AI mode. Use: idle, combat, patrol, guard, follow, flee. Combine with +."));
            return 0;
        }
        AiResult result = state(source.getServer()).setGroupAiModes(rawName, modes);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int describeSelectedAi(CommandSourceStack source) {
        List<String> lines = state(source.getServer()).selectedAiSummary();
        if (lines.isEmpty()) {
            source.sendFailure(Component.literal("No selected managed bots to describe."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.join(" | ", lines)), false);
        return lines.size();
    }

    public static int describeGroupAi(CommandSourceStack source, String rawName) {
        String summary = state(source.getServer()).groupAiSummary(rawName);
        if (summary == null) {
            source.sendFailure(Component.literal("Unknown group: " + rawName));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(summary), false);
        return 1;
    }

    public static void handleGuiClosed(ServerPlayer player) {
        // GUI removed; kept as a no-op for compatibility with older clients.
    }

    public static void handlePlayerDisconnect(ServerPlayer player) {
        if (player == null || player.createCommandSourceStack().getServer() == null) {
            return;
        }
        state(player.createCommandSourceStack().getServer()).handlePlayerDisconnect(player);
    }

    public static void requestState(ServerPlayer player, boolean openScreen) {
        // GUI removed; kept as a no-op for compatibility with older clients.
    }

    public static void applyLimitFromGui(ServerPlayer player, int value) {
        setLimit(player.createCommandSourceStack(), value);
    }

    public static void applySpawnsPerTickFromGui(ServerPlayer player, int value) {
        setSpawnsPerTick(player.createCommandSourceStack(), value);
    }

    public static void applyDebugFromGui(ServerPlayer player, boolean enabled) {
        setDebug(player.createCommandSourceStack(), enabled);
    }

    public static void requestSummonFromGui(ServerPlayer player, int count, String rawNames, String formation) {
        requestSummon(player.createCommandSourceStack(), count, rawNames, BotConfig.decode(formation));
    }

    public static void runSelectedActionFromGui(ServerPlayer player, String action) {
        runSelectedAction(player.createCommandSourceStack(), action);
    }

    public static void runSelectedActionSetFromGui(ServerPlayer player, String actions) {
        runSelectedActionSet(player.createCommandSourceStack(), actions);
    }

    public static void teleportSelectionFromGui(ServerPlayer player, String direction, String blockName) {
        teleportSelection(player.createCommandSourceStack(), direction, blockName);
    }

    public static void clearSelectionFromGui(ServerPlayer player) {
        clearSelection(player.createCommandSourceStack());
    }

    public static void giveWandFromGui(ServerPlayer player) {
        giveSelectionWand(player.createCommandSourceStack());
    }

    public static void selectAllFromGui(ServerPlayer player) {
        selectAll(player.createCommandSourceStack());
    }

    public static void selectRangeFromGui(ServerPlayer player, int range) {
        selectWithinRange(player.createCommandSourceStack(), range);
    }

    public static void selectClosestFromGui(ServerPlayer player, int count) {
        selectClosest(player.createCommandSourceStack(), count);
    }

    public static void createGroupFromGui(ServerPlayer player, String name) {
        createGroup(player.createCommandSourceStack(), name);
    }

    public static void assignSelectionToGroupFromGui(ServerPlayer player, String name) {
        assignSelectionToGroup(player.createCommandSourceStack(), name);
    }

    public static void removeSelectionFromGroupFromGui(ServerPlayer player, String name) {
        removeSelectionFromGroup(player.createCommandSourceStack(), name);
    }

    public static void setSelectedAiModeFromGui(ServerPlayer player, String mode) {
        setSelectedAiMode(player.createCommandSourceStack(), mode);
    }

    public static void setGroupAiModeFromGui(ServerPlayer player, String group, String mode) {
        setGroupAiMode(player.createCommandSourceStack(), group, mode);
    }

    public static void applySelectedItemFromGui(ServerPlayer player, String slot, String item, int count) {
        applySelectedItem(player.createCommandSourceStack(), slot, item, count);
    }

    public static void applySelectedHotbarSlotFromGui(ServerPlayer player, int slotIndex, String item, int count) {
        applySelectedHotbarSlot(player.createCommandSourceStack(), slotIndex, item, count);
    }

    public static void applySelectedEffectFromGui(ServerPlayer player, String effect, int durationSeconds, int amplifier) {
        applySelectedEffect(player.createCommandSourceStack(), effect, durationSeconds, amplifier);
    }

    public static void clearSelectedEffectsFromGui(ServerPlayer player) {
        clearSelectedEffects(player.createCommandSourceStack());
    }

    public static boolean toggleSelection(ServerPlayer actor, Entity entity) {
        EntityPlayerMPFake fakePlayer = resolveManagedBot(actor.createCommandSourceStack().getServer(), entity);
        if (fakePlayer == null) {
            return false;
        }

        boolean selected = state(actor.createCommandSourceStack().getServer()).toggleSelection(fakePlayer);
        actor.displayClientMessage(
                Component.literal((selected ? "Selected " : "Deselected ") + fakePlayer.getGameProfile().name())
                        .withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                true
        );
        return true;
    }

    public static void tick(MinecraftServer server) {
        state(server).tick();
    }

    public static void clear(MinecraftServer server) {
        ServerState removed = SERVER_STATES.remove(server);
        if (removed != null) {
            removed.shutdown();
        }
    }

    public static PlayerBatchSnapshot snapshot(MinecraftServer server, boolean openScreen) {
        ServerState state = state(server);
        List<String> selectedNames = state.selectedNames();
        BatchProgress progress = state.progress();
        return new PlayerBatchSnapshot(
                openScreen,
                PlayerBatchConfig.getMaxSummonCount(),
                PlayerBatchConfig.getMaxSpawnsPerTick(),
                PlayerBatchConfig.isDebugEnabled(),
                progress.active(),
                progress.total(),
                progress.successCount(),
                progress.failCount(),
                progress.pendingCount(),
                state.queueDepth(),
                selectedNames,
                state.groupSummaries()
        );
    }

    public static void broadcastState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        state(server).broadcast(false);
    }

    private static ServerState state(MinecraftServer server) {
        return SERVER_STATES.computeIfAbsent(server, ServerState::new);
    }

    private static EntityPlayerMPFake resolveManagedBot(MinecraftServer server, Entity entity) {
        if (!(entity instanceof EntityPlayerMPFake fakePlayer) || server == null) {
            return null;
        }
        ServerState serverState = state(server);
        if (!serverState.isManagedBot(fakePlayer)) {
            return null;
        }
        serverState.ensureBotTag(fakePlayer);
        return fakePlayer;
    }

    private static Direction parseDirection(String directionName) {
        if (directionName == null) {
            return null;
        }

        return switch (directionName.toLowerCase(Locale.ROOT)) {
            case "up" -> Direction.UP;
            case "below", "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
    }

    private static boolean isKnownBlock(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return false;
        }

        String normalized = normalizeBlockId(blockName);
        return BuiltInRegistries.BLOCK.keySet().stream().anyMatch(key -> key.toString().equals(normalized));
    }

    private static String normalizeBlockId(String blockName) {
        return blockName.contains(":") ? blockName.toLowerCase(Locale.ROOT) : "minecraft:" + blockName.toLowerCase(Locale.ROOT);
    }

    private static EquipmentSlot parseEquipmentSlot(String rawSlot) {
        if (rawSlot == null || rawSlot.isBlank()) {
            return null;
        }
        return switch (rawSlot.trim().toLowerCase(Locale.ROOT)) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "mainhand", "hand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static Item parseItem(String rawItem) {
        String normalizedId = normalizeRegistryId(rawItem);
        if (normalizedId == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.keySet().stream()
                .filter(key -> key.toString().equals(normalizedId))
                .findFirst()
                .flatMap(key -> BuiltInRegistries.ITEM.get(key).map(net.minecraft.core.Holder.Reference::value))
                .orElse(null);
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> parseEffect(String rawEffect) {
        String normalizedId = normalizeRegistryId(rawEffect);
        if (normalizedId == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .filter(key -> key.toString().equals(normalizedId))
                .findFirst()
                .flatMap(BuiltInRegistries.MOB_EFFECT::get)
                .map(holder -> (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) holder)
                .orElse(null);
    }

    private static String normalizeRegistryId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return rawId.contains(":") ? rawId.trim().toLowerCase(Locale.ROOT) : "minecraft:" + rawId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePlayerName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public record PlayerBatchSnapshot(
            boolean openScreen,
            int maxSummonCount,
            int maxSpawnsPerTick,
            boolean debugEnabled,
            boolean batchActive,
            int totalCount,
            int successCount,
            int failCount,
            int pendingCount,
            int queuedBatches,
            List<String> selectedNames,
            List<String> groups
    ) {
    }

    public record BatchProgress(boolean active, int total, int successCount, int failCount, int pendingCount) {
        public static BatchProgress empty() {
            return new BatchProgress(false, 0, 0, 0, 0);
        }
    }

    private static final class ServerState {
        private final MinecraftServer server;
        private final Deque<SummonBatch> queue = new ArrayDeque<>();
        private final Set<UUID> selectedIds = new LinkedHashSet<>();
        private final Set<UUID> subscribers = new LinkedHashSet<>();
        private final Map<String, BotGroup> groups = new LinkedHashMap<>();
        private final Map<UUID, BotBrain> brains = new HashMap<>();
        private final Set<UUID> managedBotIds = new LinkedHashSet<>();
        private final Set<String> managedBotNames = new LinkedHashSet<>();
        private SummonBatch activeBatch;
        private int syncCooldown;
        private int aiTickCooldown;

        private ServerState(MinecraftServer server) {
            this.server = server;
        }

        private int queueBatch(CommandSourceStack source, List<String> names, String formation) {
            return queueBatch(source, names, new BotConfig(formation, new BotLoadout()));
        }

        private int queueBatch(CommandSourceStack source, List<String> names, BotConfig config) {
            if (names.isEmpty()) {
                return 0;
            }
            List<String> reservedNames = reservedNames();
            List<String> uniqueNames = new ArrayList<>(names.size());
            for (String name : names) {
                String uniqueName = makeUniqueSummonName(name, reservedNames);
                uniqueNames.add(uniqueName);
                reservedNames.add(uniqueName);
            }
            queue.addLast(new SummonBatch(this, server, source, uniqueNames, config));
            debug("Queued summon batch with {} names using {} formation", uniqueNames.size(), config.formation());
            broadcast(false);
            return uniqueNames.size();
        }

        private void tick() {
            cleanupSelection();
            cleanupSubscribers();
            cleanupGroupsAndBrains();
            tagQueuedBots();

            if (activeBatch == null && !queue.isEmpty()) {
                activeBatch = queue.removeFirst();
                activeBatch.start();
                broadcast(false);
            }

            if (activeBatch != null) {
                activeBatch.tick(PlayerBatchConfig.getMaxSpawnsPerTick());
                if (activeBatch.isComplete()) {
                    activeBatch.finish();
                    activeBatch = null;
                    broadcast(false);
                }
            }

            if (!subscribers.isEmpty()) {
                if (activeBatch != null || syncCooldown <= 0) {
                    broadcast(false);
                    syncCooldown = 5;
                } else {
                    syncCooldown--;
                }
            }

            if (aiTickCooldown <= 0) {
                tickAi();
                aiTickCooldown = AI_TICK_INTERVAL;
            }
            aiTickCooldown--;
        }

        private void tagQueuedBots() {
            if (activeBatch != null) {
                activeBatch.tryTagKnownPlayers();
            }
            for (SummonBatch queuedBatch : queue) {
                queuedBatch.tryTagKnownPlayers();
            }
        }

        private int clearSelection() {
            List<EntityPlayerMPFake> selectedPlayers = selectedPlayers();
            for (EntityPlayerMPFake selectedPlayer : selectedPlayers) {
                removeSelectionGlow(selectedPlayer);
            }
            int cleared = selectedIds.size();
            selectedIds.clear();
            debug("Cleared {} selections", cleared);
            broadcast(false);
            return cleared;
        }

        private int selectAll() {
            int count = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player instanceof EntityPlayerMPFake fakePlayer && isManagedBot(fakePlayer)) {
                    ensureBotTag(fakePlayer);
                    if (selectedIds.add(fakePlayer.getUUID())) {
                        applySelectionGlow(fakePlayer);
                    } else {
                        applySelectionGlow(fakePlayer);
                    }
                    count++;
                }
            }
            broadcast(false);
            return count;
        }

        private int selectWithinRange(Vec3 center, double range) {
            double rangeSquared = range * range;
            List<EntityPlayerMPFake> candidates = fakePlayers().stream()
                    .filter(fakePlayer -> fakePlayer.position().distanceToSqr(center) <= rangeSquared)
                    .sorted(Comparator.comparingDouble(fakePlayer -> fakePlayer.position().distanceToSqr(center)))
                    .toList();
            return selectCandidates(candidates);
        }

        private int selectClosest(Vec3 center, int count) {
            List<EntityPlayerMPFake> candidates = fakePlayers().stream()
                    .sorted(Comparator.comparingDouble(fakePlayer -> fakePlayer.position().distanceToSqr(center)))
                    .limit(count)
                    .toList();
            return selectCandidates(candidates);
        }

        private int selectCandidates(List<EntityPlayerMPFake> candidates) {
            int count = 0;
            for (EntityPlayerMPFake candidate : candidates) {
                selectedIds.add(candidate.getUUID());
                applySelectionGlow(candidate);
                count++;
            }
            broadcast(false);
            return count;
        }

        private boolean toggleSelection(EntityPlayerMPFake player) {
            UUID id = player.getUUID();
            boolean selected;
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
                removeSelectionGlow(player);
                selected = false;
            } else {
                selectedIds.add(id);
                applySelectionGlow(player);
                selected = true;
            }
            debug("{} selection for fake player {}", selected ? "Added" : "Removed", player.getGameProfile().name());
            broadcast(false);
            return selected;
        }

        private List<String> selectedNames() {
            List<String> names = new ArrayList<>();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                names.add(player.getGameProfile().name());
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }

        private int runAction(CommandSourceStack source, String action) {
            if (action == null || action.isBlank()) {
                return 0;
            }
            int succeeded = 0;
            CommandSourceStack executionSource = source.withSuppressedOutput();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                String command = "player " + player.getGameProfile().name() + " " + action.trim();
                boolean success = CommandCompat.performPrefixedCommand(executionSource, command);
                if (success) {
                    succeeded++;
                    debug("Ran selected action '/{}'", command);
                } else {
                    PlayerBatch.LOGGER.warn("Selected action failed: /{}", command);
                }
            }
            broadcast(false);
            return succeeded;
        }

        private int applySelectedItem(EquipmentSlot slot, Item item, int count) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            int sanitizedCount = Math.max(1, count);
            for (EntityPlayerMPFake player : players) {
                player.setItemSlot(slot, new ItemStack(item, sanitizedCount));
            }
            broadcast(false);
            return players.size();
        }

        private int applySelectedLoadout(BotLoadout loadout) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            for (EntityPlayerMPFake player : players) {
                clearBotLoadout(player);
                loadout.applyTo(player);
                if (selectedIds.contains(player.getUUID())) {
                    applySelectionGlow(player);
                }
            }
            broadcast(false);
            return players.size();
        }

        private int applySelectedHotbarSlot(int slotIndex, Item item, int count) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            int sanitizedCount = Math.max(1, count);
            for (EntityPlayerMPFake player : players) {
                player.getInventory().setItem(slotIndex, new ItemStack(item, sanitizedCount));
            }
            broadcast(false);
            return players.size();
        }

        private void clearBotLoadout(EntityPlayerMPFake player) {
            for (EquipmentSlot slot : List.of(
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND
            )) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.removeAllEffects();
        }

        private int applySelectedEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectHolder, int durationSeconds, int amplifier) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            int durationTicks = Math.max(1, durationSeconds) * 20;
            int level = Math.max(0, amplifier);
            for (EntityPlayerMPFake player : players) {
                player.addEffect(new MobEffectInstance(effectHolder, durationTicks, level));
            }
            broadcast(false);
            return players.size();
        }

        private int clearSelectedEffects() {
            List<EntityPlayerMPFake> players = selectedPlayers();
            for (EntityPlayerMPFake player : players) {
                player.removeAllEffects();
                if (selectedIds.contains(player.getUUID())) {
                    applySelectionGlow(player);
                }
            }
            broadcast(false);
            return players.size();
        }

        private int teleportSelection(CommandSourceStack source, Direction direction, String blockName) {
            try {
                List<EntityPlayerMPFake> players = selectedPlayers();
                if (players.isEmpty()) {
                    return 0;
                }

                ServerLevel level = (ServerLevel) players.getFirst().level();
                Vec3 searchCenter = averagePosition(players);
                List<BlockPos> targets = findNearestBlocks(level, searchCenter, blockName, players.size());
                if (targets.isEmpty()) {
                    return 0;
                }

                int moved = 0;
                for (int index = 0; index < players.size() && index < targets.size(); index++) {
                    EntityPlayerMPFake player = players.get(index);
                    BlockPos target = targets.get(index).relative(direction);
                    boolean success = player.teleportTo(
                            level,
                            target.getX() + 0.5D,
                            target.getY(),
                            target.getZ() + 0.5D,
                            Set.of(),
                            player.getYRot(),
                            player.getXRot(),
                            false
                    );
                    if (success) {
                        moved++;
                        debug("Teleported {} to {} near {}", player.getGameProfile().name(), target, blockName);
                    } else {
                        PlayerBatch.LOGGER.warn("Direct teleport failed for {} to {}", player.getGameProfile().name(), target);
                    }
                }
                broadcast(false);
                return moved;
            } catch (Exception exception) {
                PlayerBatch.LOGGER.error("PlayerBatch teleportSelection failed for direction={} block={}", direction, blockName, exception);
                return 0;
            }
        }

        private List<EntityPlayerMPFake> selectedPlayers() {
            List<EntityPlayerMPFake> players = new ArrayList<>();
            List<UUID> invalid = new ArrayList<>();
            for (UUID selectedId : selectedIds) {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(selectedId);
                if (serverPlayer instanceof EntityPlayerMPFake fakePlayer) {
                    players.add(fakePlayer);
                } else {
                    invalid.add(selectedId);
                }
            }
            if (!invalid.isEmpty()) {
                selectedIds.removeAll(invalid);
            }
            players.sort(Comparator.comparing(player -> player.getGameProfile().name(), String::compareToIgnoreCase));
            return players;
        }

        private List<EntityPlayerMPFake> fakePlayers() {
            return server.getPlayerList().getPlayers().stream()
                    .filter(player -> player instanceof EntityPlayerMPFake fakePlayer && isManagedBot(fakePlayer))
                    .map(player -> (EntityPlayerMPFake) player)
                    .filter(Objects::nonNull)
                    .toList();
        }

        private boolean isManagedBot(EntityPlayerMPFake fakePlayer) {
            return fakePlayer.getTags().contains(BOT_TAG)
                    || managedBotIds.contains(fakePlayer.getUUID())
                    || managedBotNames.contains(normalizePlayerName(fakePlayer.getGameProfile().name()));
        }

        private void markManagedName(String name) {
            String normalized = normalizePlayerName(name);
            if (normalized != null) {
                managedBotNames.add(normalized);
            }
        }

        private void markManagedBot(EntityPlayerMPFake fakePlayer) {
            managedBotIds.add(fakePlayer.getUUID());
            markManagedName(fakePlayer.getGameProfile().name());
            ensureBotTag(fakePlayer);
        }

        private void forgetManagedBot(EntityPlayerMPFake fakePlayer) {
            clearSelectionState(fakePlayer);
            brains.remove(fakePlayer.getUUID());
            managedBotIds.remove(fakePlayer.getUUID());
            String normalized = normalizePlayerName(fakePlayer.getGameProfile().name());
            if (normalized != null) {
                managedBotNames.remove(normalized);
            }
            for (BotGroup group : groups.values()) {
                group.memberIds().remove(fakePlayer.getUUID());
            }
        }

        private void clearSelectionState(EntityPlayerMPFake fakePlayer) {
            selectedIds.remove(fakePlayer.getUUID());
            removeSelectionGlow(fakePlayer);
        }

        private void ensureBotTag(EntityPlayerMPFake fakePlayer) {
            if (!fakePlayer.getTags().contains(BOT_TAG)) {
                fakePlayer.addTag(BOT_TAG);
            }
            managedBotIds.add(fakePlayer.getUUID());
            markManagedName(fakePlayer.getGameProfile().name());
        }

        private GroupResult createGroup(String rawName) {
            String key = normalizeGroupName(rawName);
            if (key == null) {
                return GroupResult.failure("Group name must use 1-32 letters, numbers, underscores, or hyphens.");
            }
            if (groups.containsKey(key)) {
                return GroupResult.failure("Group already exists: " + rawName);
            }
            groups.put(key, new BotGroup(rawName));
            broadcast(false);
            return GroupResult.success("Created group '" + rawName + "'.", 1);
        }

        private GroupResult assignSelectionToGroup(String rawName) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return GroupResult.failure("Unknown group: " + rawName);
            }
            List<EntityPlayerMPFake> players = selectedPlayers();
            if (players.isEmpty()) {
                return GroupResult.failure("Select one or more managed bots first.");
            }
            int added = 0;
            for (EntityPlayerMPFake player : players) {
                BotBrain brain = ensureBrain(player);
                group.memberIds().add(player.getUUID());
                brain.groupKey = normalizeGroupName(group.displayName());
                if (!brain.modes.equals(group.sharedModes())) {
                    brain.modes = EnumSet.copyOf(group.sharedModes());
                }
                added++;
            }
            broadcast(false);
            return GroupResult.success("Assigned " + added + " selected bot" + suffix(added) + " to group '" + group.displayName() + "'.", added);
        }

        private GroupResult removeSelectionFromGroup(String rawName) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return GroupResult.failure("Unknown group: " + rawName);
            }
            List<EntityPlayerMPFake> players = selectedPlayers();
            if (players.isEmpty()) {
                return GroupResult.failure("Select one or more managed bots first.");
            }
            int removed = 0;
            for (EntityPlayerMPFake player : players) {
                if (group.memberIds().remove(player.getUUID())) {
                    BotBrain brain = ensureBrain(player);
                    if (Objects.equals(brain.groupKey, normalizeGroupName(group.displayName()))) {
                        brain.groupKey = null;
                    }
                    removed++;
                }
            }
            broadcast(false);
            return GroupResult.success("Removed " + removed + " selected bot" + suffix(removed) + " from group '" + group.displayName() + "'.", removed);
        }

        private List<String> groupSummaries() {
            List<String> summaries = new ArrayList<>();
            for (BotGroup group : groups.values()) {
                summaries.add(group.displayName() + "=" + group.memberIds().size() + " [" + BotAiMode.displayModes(group.sharedModes()) + "]");
            }
            return summaries;
        }

        private AiResult setSelectedAiModes(EnumSet<BotAiMode> modes) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            if (players.isEmpty()) {
                return AiResult.failure("Select one or more managed bots first.");
            }
            for (EntityPlayerMPFake player : players) {
                ensureBrain(player).modes = EnumSet.copyOf(modes);
            }
            broadcast(false);
            return AiResult.success("Set AI mode to '" + BotAiMode.displayModes(modes) + "' for " + players.size() + " selected bot" + suffix(players.size()) + ".", players.size());
        }

        private AiResult setAllAiModes(EnumSet<BotAiMode> modes) {
            List<EntityPlayerMPFake> players = fakePlayers();
            if (players.isEmpty()) {
                return AiResult.failure("No managed bots are available.");
            }
            for (EntityPlayerMPFake player : players) {
                ensureBrain(player).modes = EnumSet.copyOf(modes);
            }
            broadcast(false);
            return AiResult.success("Set AI mode to '" + BotAiMode.displayModes(modes) + "' for all " + players.size() + " managed bot" + suffix(players.size()) + ".", players.size());
        }

        private void applyCombatPreset(EntityPlayerMPFake fakePlayer, CombatPresetSpec combatPreset) {
            BotBrain brain = ensureBrain(fakePlayer);
            brain.combatPreset = combatPreset;
            brain.healCooldownTicks = 0;
            brain.attackRetreatTicks = 0;
            brain.flexSpinTicks = 0;
            brain.flexSpinDirection = 1;
            brain.flexSpinProgress = 0.0F;
            brain.stuckTicks = 0;
            brain.scriptedUse = null;
            stopActionMovement(fakePlayer, true);
            if (combatPreset != null) {
                brain.modes = EnumSet.of(BotAiMode.COMBAT);
            } else if (brain.groupKey == null) {
                brain.modes = EnumSet.of(BotAiMode.IDLE);
            }
        }

        private AiResult setGroupAiModes(String rawName, EnumSet<BotAiMode> modes) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return AiResult.failure("Unknown group: " + rawName);
            }
            group.sharedModes = EnumSet.copyOf(modes);
            int affected = 0;
            for (UUID memberId : List.copyOf(group.memberIds())) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player instanceof EntityPlayerMPFake fakePlayer && isManagedBot(fakePlayer)) {
                    ensureBrain(fakePlayer).modes = EnumSet.copyOf(modes);
                    affected++;
                }
            }
            broadcast(false);
            return AiResult.success("Set group '" + group.displayName() + "' AI mode to '" + BotAiMode.displayModes(modes) + "' for " + affected + " bot" + suffix(affected) + ".", affected);
        }

        private List<String> selectedAiSummary() {
            List<String> summary = new ArrayList<>();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                BotBrain brain = ensureBrain(player);
                String groupText = brain.groupKey == null ? "ungrouped" : groups.getOrDefault(brain.groupKey, new BotGroup(brain.groupKey)).displayName();
                summary.add(player.getGameProfile().name() + "=" + BotAiMode.displayModes(brain.modes) + " (" + groupText + ")");
            }
            return summary;
        }

        private String groupAiSummary(String rawName) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return null;
            }
            return "Group '" + group.displayName() + "' has " + group.memberIds().size() + " bot" + suffix(group.memberIds().size()) + " with shared AI mode '" + BotAiMode.displayModes(group.sharedModes()) + "'.";
        }

        private void addSubscriber(UUID subscriber) {
        }

        private void removeSubscriber(UUID subscriber) {
        }

        private void handlePlayerDisconnect(ServerPlayer player) {
            if (player instanceof EntityPlayerMPFake fakePlayer && isManagedBot(fakePlayer)) {
                clearSelectionState(fakePlayer);
                cleanupManagedBot(fakePlayer);
                forgetManagedBot(fakePlayer);
            }
            broadcast(false);
        }

        private int queueDepth() {
            return queue.size() + (activeBatch == null ? 0 : 1);
        }

        private BatchProgress progress() {
            return activeBatch == null ? BatchProgress.empty() : activeBatch.progress();
        }

        private void broadcast(boolean openScreen) {
        }

        private void cleanupSelection() {
            selectedPlayers();
        }

        private void cleanupSubscribers() {
        }

        private List<String> reservedNames() {
            List<String> reserved = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                reserved.add(player.getGameProfile().name().toLowerCase(Locale.ROOT));
            }
            if (activeBatch != null) {
                activeBatch.collectReservedNames(reserved);
            }
            for (SummonBatch queuedBatch : queue) {
                queuedBatch.collectReservedNames(reserved);
            }
            return reserved;
        }

        private void shutdown() {
            if (activeBatch != null) {
                activeBatch.discard();
                activeBatch = null;
            }
            stripAllManagedBots();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                removeSelectionGlow(player);
            }
            selectedIds.clear();
            subscribers.clear();
            groups.clear();
            brains.clear();
            managedBotIds.clear();
            managedBotNames.clear();
            queue.clear();
        }

        private void stripAllManagedBots() {
            for (EntityPlayerMPFake fakePlayer : fakePlayers()) {
                cleanupManagedBot(fakePlayer);
            }
        }

        private void cleanupGroupsAndBrains() {
            Set<UUID> liveBots = new HashSet<>();
            Set<String> liveNames = new HashSet<>();
            for (EntityPlayerMPFake fakePlayer : fakePlayers()) {
                ensureBotTag(fakePlayer);
                liveBots.add(fakePlayer.getUUID());
                liveNames.add(normalizePlayerName(fakePlayer.getGameProfile().name()));
                ensureBrain(fakePlayer);
            }
            brains.keySet().removeIf(id -> !liveBots.contains(id));
            managedBotIds.retainAll(liveBots);
            managedBotNames.retainAll(liveNames);
            for (BotGroup group : groups.values()) {
                group.memberIds().removeIf(id -> !liveBots.contains(id));
            }
        }

        private BotBrain ensureBrain(EntityPlayerMPFake player) {
            return brains.computeIfAbsent(player.getUUID(), id -> new BotBrain());
        }

        private void tickAi() {
            for (EntityPlayerMPFake fakePlayer : fakePlayers()) {
                BotBrain brain = ensureBrain(fakePlayer);
                tickBrain(fakePlayer, brain);
            }
        }

        private void tickBrain(EntityPlayerMPFake fakePlayer, BotBrain brain) {
            EnumSet<BotAiMode> modes = brain.modes.isEmpty() ? EnumSet.of(BotAiMode.IDLE) : EnumSet.copyOf(brain.modes);
            CombatPresetSpec combatPreset = brain.combatPreset;

            ServerPlayer followTarget = modes.contains(BotAiMode.FOLLOW) ? findNearestPlayerTarget(fakePlayer, -1.0D) : null;
            LivingEntity threat = (modes.contains(BotAiMode.GUARD) || modes.contains(BotAiMode.COMBAT) || modes.contains(BotAiMode.FLEE) || combatPreset != null)
                    ? findNearestThreat(fakePlayer, 16.0D)
                    : null;

            if (brain.healCooldownTicks > 0) {
                brain.healCooldownTicks--;
            }
            if (brain.attackRetreatTicks > 0) {
                brain.attackRetreatTicks--;
            }
            if (brain.flexSpinTicks <= 0 && brain.flexSpinProgress != 0.0F) {
                brain.flexSpinProgress = 0.0F;
            }
            if (tickScriptedUse(fakePlayer, brain, threat)) {
                return;
            }

            if (combatPreset != null) {
                manageCombatInventory(fakePlayer, combatPreset);
                ItemEntity desiredPickup = findDesiredPickup(fakePlayer, combatPreset);
                if (combatPreset.selfHealEnabled() && hasLowHealth(fakePlayer)) {
                    HealingChoice healingChoice = findBestHealingChoice(fakePlayer);
                    if (healingChoice != null) {
                        if (brain.healCooldownTicks <= 0 && beginHealingSequence(fakePlayer, brain, healingChoice, threat != null)) {
                            debug("Started scripted healing for {}", fakePlayer.getGameProfile().name());
                        }
                        return;
                    }
                    if (desiredPickup != null && isHealingPickup(desiredPickup.getItem())) {
                        prepareInventorySpaceFor(fakePlayer, desiredPickup.getItem(), combatPreset);
                        lookAtTarget(fakePlayer, desiredPickup);
                        moveTowardTarget(fakePlayer, desiredPickup, 1.0D, 0.95F, false, brain);
                        return;
                    }
                }
                if (desiredPickup != null) {
                    prepareInventorySpaceFor(fakePlayer, desiredPickup.getItem(), combatPreset);
                    lookAtTarget(fakePlayer, desiredPickup);
                    moveTowardTarget(fakePlayer, desiredPickup, 1.0D, 0.95F, false, brain);
                    return;
                }
            }

            if (modes.contains(BotAiMode.PATROL)) {
                float nextYaw = fakePlayer.getYRot() + 25.0F;
                fakePlayer.setYRot(nextYaw);
                fakePlayer.setYHeadRot(nextYaw);
            }

            if (modes.contains(BotAiMode.FLEE) && threat != null) {
                moveAwayFromThreat(fakePlayer, threat, 1.0F, brain);
                return;
            }

            if (modes.contains(BotAiMode.COMBAT) && threat != null) {
                if (combatPreset != null && combatPreset.flex360Enabled() && brain.flexSpinTicks > 0) {
                    tick360Flex(fakePlayer, threat, brain);
                    return;
                }
                lookAtCombatTarget(fakePlayer, threat, combatPreset);
                double reach = combatPreset == null ? 3.0D : combatPreset.reach();
                double preferredDistance = combatPreset != null && combatPreset.stapEnabled()
                        ? 3.0D
                        : Math.max(1.6D, reach - 0.25D);
                if (brain.attackRetreatTicks > 0 && combatPreset != null && combatPreset.stapEnabled()) {
                    moveAwayFromThreat(fakePlayer, threat, 1.0F, brain);
                } else if (horizontalDistance(fakePlayer, threat) > preferredDistance || Math.abs(threat.getY() - fakePlayer.getY()) > 1.25D) {
                    moveTowardTarget(fakePlayer, threat, preferredDistance, 1.0F, combatPreset != null && combatPreset.stapEnabled(), brain);
                } else {
                    stopActionMovement(fakePlayer, false);
                    tryAttackTarget(fakePlayer, threat, brain, combatPreset);
                }
                return;
            }

            if (modes.contains(BotAiMode.FOLLOW) && followTarget != null) {
                lookAtTarget(fakePlayer, followTarget);
                moveTowardTarget(fakePlayer, followTarget, 2.5D, 0.9F, true, brain);
                return;
            }

            if (modes.contains(BotAiMode.GUARD) && threat != null) {
                lookAtTarget(fakePlayer, threat);
                stopActionMovement(fakePlayer, false);
                return;
            }

            if (!modes.contains(BotAiMode.PATROL)) {
                stopActionMovement(fakePlayer, false);
            }
        }

        private ServerPlayer findNearestPlayerTarget(EntityPlayerMPFake source, double range) {
            return server.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(source.getUUID()))
                    .filter(player -> !(player instanceof EntityPlayerMPFake))
                    .filter(player -> player.level() == source.level())
                    .filter(player -> range <= 0.0D || player.distanceToSqr(source) <= range * range)
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(source)))
                    .orElse(null);
        }

        private LivingEntity findNearestThreat(EntityPlayerMPFake source, double range) {
            return source.level().getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(range), entity ->
                            !entity.getUUID().equals(source.getUUID()) && !(entity instanceof EntityPlayerMPFake managed && managed.getTags().contains(BOT_TAG)))
                    .stream()
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(source)))
                    .orElse(null);
        }

        private void lookAtTarget(EntityPlayerMPFake source, Entity target) {
            if (target == null) {
                return;
            }
            source.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position().add(0.0D, target.getEyeHeight(), 0.0D));
        }

        private EntityPlayerActionPack actionPack(EntityPlayerMPFake source) {
            return ((ServerPlayerInterface) source).getActionPack();
        }

        private void lookAtCombatTarget(EntityPlayerMPFake source, Entity target, CombatPresetSpec combatPreset) {
            if (target == null) {
                return;
            }
            lookAtTarget(source, target);
            if (combatPreset != null && combatPreset.stapEnabled()) {
                actionPack(source).turn(0.0F, 6.0F);
            }
        }

        private void moveTowardTarget(EntityPlayerMPFake source, Entity target, double preferredDistance, float forwardPower, boolean cautiousAtEdges, BotBrain brain) {
            if (target == null) {
                stopActionMovement(source, false);
                return;
            }

            Vec3 offset = target.position().subtract(source.position());
            double distance = offset.length();
            if (distance <= preferredDistance || distance < 0.001D) {
                stopActionMovement(source, false);
                return;
            }

            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            if (horizontal.lengthSqr() < 0.0001D) {
                stopActionMovement(source, false);
                return;
            }

            EntityPlayerActionPack actionPack = actionPack(source);
            lookAtTarget(source, target);
            float desiredForward = Math.max(0.35F, Math.min(1.0F, forwardPower));
            Vec3 motion = horizontal.normalize().scale(Math.max(0.2D, forwardPower));
            boolean shouldJump = shouldJumpTowardTarget(source, target, motion, brain);
            boolean shouldSneak = cautiousAtEdges && shouldSneakTowardTarget(source, target, motion);
            actionPack.setSneaking(shouldSneak);
            actionPack.setSprinting(distance > preferredDistance + 2.0D && !shouldSneak);
            actionPack.setStrafing(brain.unstuckStrafeTicks > 0 ? brain.unstuckStrafeDirection * 0.7F : 0.0F);
            actionPack.setForward(desiredForward);
            if (shouldJump) {
                actionPack.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
            }
            updateStuckState(source, brain, true, target);
        }

        private void stopActionMovement(EntityPlayerMPFake source, boolean stopUse) {
            EntityPlayerActionPack actionPack = actionPack(source);
            actionPack.setForward(0.0F);
            actionPack.setStrafing(0.0F);
            actionPack.setSneaking(false);
            actionPack.setSprinting(false);
            if (stopUse) {
                actionPack.start(EntityPlayerActionPack.ActionType.USE, null);
            }
        }

        private void moveAwayFromThreat(EntityPlayerMPFake source, Entity threat, float backwardPower, BotBrain brain) {
            Vec3 away = source.position().subtract(threat.position());
            if (away.lengthSqr() < 0.0001D) {
                stopActionMovement(source, false);
                return;
            }
            EntityPlayerActionPack actionPack = actionPack(source);
            lookAtTarget(source, threat);
            actionPack.setSneaking(false);
            actionPack.setSprinting(false);
            actionPack.setStrafing(brain.unstuckStrafeTicks > 0 ? brain.unstuckStrafeDirection * 0.5F : 0.0F);
            actionPack.setForward(-Math.max(0.35F, Math.min(1.0F, backwardPower)));
            if (shouldJumpTowardTarget(source, threat, away.normalize().scale(0.7D), brain)) {
                actionPack.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
            }
            updateStuckState(source, brain, true, threat);
        }

        private void tick360Flex(EntityPlayerMPFake source, LivingEntity target, BotBrain brain) {
            EntityPlayerActionPack actionPack = actionPack(source);
            actionPack.setSneaking(false);
            actionPack.setSprinting(false);
            actionPack.setForward(0.0F);
            actionPack.setStrafing(0.0F);
            float yawDelta = 45.0F * brain.flexSpinDirection;
            applyVisibleYawSpin(source, yawDelta);
            brain.flexSpinProgress += yawDelta;
            if (Math.abs(brain.flexSpinProgress) >= 360.0F) {
                brain.flexSpinProgress = 0.0F;
                brain.flexSpinDirection *= -1;
            }
            brain.flexSpinTicks--;
            updateStuckState(source, brain, true, target);
        }

        private void applyVisibleYawSpin(EntityPlayerMPFake source, float yawDelta) {
            float spunYaw = source.getYRot() + yawDelta;
            source.setYRot(spunYaw);
            source.yRotO = spunYaw;
            source.setYHeadRot(spunYaw);
            source.yHeadRotO = spunYaw;
            source.yBodyRot = spunYaw;
            source.yBodyRotO = spunYaw;
            ServerLevel level = (ServerLevel) source.level();
            byte headYaw = (byte) Math.floor(spunYaw * 256.0F / 360.0F);
            byte pitch = (byte) Math.floor(source.getXRot() * 256.0F / 360.0F);
            level.getServer().getPlayerList().broadcastAll(new ClientboundMoveEntityPacket.Rot(source.getId(), headYaw, pitch, source.onGround()), level.dimension());
            level.getServer().getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(source, headYaw), level.dimension());
        }

        private boolean hasLowHealth(EntityPlayerMPFake fakePlayer) {
            return fakePlayer.getMaxHealth() > 0.0F && (fakePlayer.getHealth() / fakePlayer.getMaxHealth()) <= 0.45F;
        }

        private void manageCombatInventory(EntityPlayerMPFake fakePlayer, CombatPresetSpec combatPreset) {
            ensureTotemOrShield(fakePlayer, combatPreset);
            equipBestArmor(fakePlayer);
            equipBestWeapon(fakePlayer);
        }

        private ItemEntity findDesiredPickup(EntityPlayerMPFake fakePlayer, CombatPresetSpec combatPreset) {
            return fakePlayer.level().getEntitiesOfClass(ItemEntity.class, fakePlayer.getBoundingBox().inflate(20.0D), itemEntity ->
                            itemEntity.isAlive() && !itemEntity.getItem().isEmpty() && isDesiredPickup(fakePlayer, itemEntity.getItem(), combatPreset))
                    .stream()
                    .min(Comparator.comparingDouble(itemEntity -> itemEntity.distanceToSqr(fakePlayer)))
                    .orElse(null);
        }

        private boolean isDesiredPickup(EntityPlayerMPFake fakePlayer, ItemStack stack, CombatPresetSpec combatPreset) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (combatPreset.selfHealEnabled() && isHealingPickup(stack)) {
                return true;
            }
            if (stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.SHIELD)) {
                return true;
            }
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                if (ArmorEvaluation.forStack(stack, slot).isBetterThan(ArmorEvaluation.forStack(fakePlayer.getItemBySlot(slot), slot))) {
                    return true;
                }
            }
            return WeaponEvaluation.forStack(stack).isBetterThan(WeaponEvaluation.forStack(fakePlayer.getMainHandItem()));
        }

        private boolean isHealingPickup(ItemStack stack) {
            return HealingChoice.forStack(stack, -1) != null;
        }

        private void prepareInventorySpaceFor(EntityPlayerMPFake fakePlayer, ItemStack desiredStack, CombatPresetSpec combatPreset) {
            if (hasInventorySpace(fakePlayer, desiredStack)) {
                return;
            }
            int dropSlot = findLeastUsefulInventorySlot(fakePlayer, desiredStack, combatPreset);
            if (dropSlot < 0) {
                return;
            }
            ItemStack dropped = fakePlayer.getInventory().removeItemNoUpdate(dropSlot);
            if (!dropped.isEmpty()) {
                fakePlayer.drop(dropped, false);
            }
        }

        private boolean hasInventorySpace(EntityPlayerMPFake fakePlayer, ItemStack desiredStack) {
            for (int slot = 0; slot < fakePlayer.getInventory().getContainerSize(); slot++) {
                ItemStack current = fakePlayer.getInventory().getItem(slot);
                if (current.isEmpty()) {
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(current, desiredStack) && current.getCount() < current.getMaxStackSize()) {
                    return true;
                }
            }
            return false;
        }

        private int findLeastUsefulInventorySlot(EntityPlayerMPFake fakePlayer, ItemStack desiredStack, CombatPresetSpec combatPreset) {
            int candidateSlot = -1;
            int candidateScore = Integer.MAX_VALUE;
            int desiredScore = usefulnessScore(fakePlayer, desiredStack, combatPreset);
            for (int slot = 0; slot < fakePlayer.getInventory().getContainerSize(); slot++) {
                ItemStack current = fakePlayer.getInventory().getItem(slot);
                if (current.isEmpty()) {
                    return slot;
                }
                int currentScore = usefulnessScore(fakePlayer, current, combatPreset);
                if (currentScore < desiredScore && currentScore < candidateScore) {
                    candidateScore = currentScore;
                    candidateSlot = slot;
                }
            }
            return candidateSlot;
        }

        private int usefulnessScore(EntityPlayerMPFake fakePlayer, ItemStack stack, CombatPresetSpec combatPreset) {
            if (stack == null || stack.isEmpty()) {
                return -1;
            }
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return 100;
            }
            if (stack.is(Items.SHIELD)) {
                return 80;
            }
            if (combatPreset.selfHealEnabled() && isHealingPickup(stack)) {
                return 70;
            }

            int bestArmorScore = -1;
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                bestArmorScore = Math.max(bestArmorScore, ArmorEvaluation.forStack(stack, slot).materialScore());
            }
            if (bestArmorScore >= 0) {
                return 40 + bestArmorScore + (stack.isEnchanted() ? 10 : 0);
            }

            WeaponEvaluation weapon = WeaponEvaluation.forStack(stack);
            if (weapon.materialScore() >= 0) {
                return 30 + weapon.materialScore() + weapon.typeScore() + (weapon.enchanted() ? 10 : 0);
            }

            return 0;
        }

        private void ensureTotemOrShield(EntityPlayerMPFake fakePlayer, CombatPresetSpec combatPreset) {
            if (combatPreset.offhandMode() != CombatPresetSpec.OffhandMode.TOTEM) {
                return;
            }
            ItemStack offhand = fakePlayer.getOffhandItem();
            if (offhand.is(Items.TOTEM_OF_UNDYING)) {
                return;
            }
            int totemSlot = findInventoryItem(fakePlayer, Items.TOTEM_OF_UNDYING);
            if (totemSlot >= 0) {
                moveInventoryItemToOffhand(fakePlayer, totemSlot);
                return;
            }
            if (!offhand.is(Items.SHIELD)) {
                int shieldSlot = findInventoryItem(fakePlayer, Items.SHIELD);
                if (shieldSlot >= 0) {
                    moveInventoryItemToOffhand(fakePlayer, shieldSlot);
                }
            }
        }

        private void moveInventoryItemToOffhand(EntityPlayerMPFake fakePlayer, int inventorySlot) {
            ItemStack fromInventory = fakePlayer.getInventory().getItem(inventorySlot);
            if (fromInventory.isEmpty()) {
                return;
            }
            ItemStack previousOffhand = fakePlayer.getOffhandItem().copy();
            fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, fromInventory.copy());
            fakePlayer.getInventory().setItem(inventorySlot, previousOffhand);
        }

        private int findInventoryItem(EntityPlayerMPFake fakePlayer, Item item) {
            for (int slot = 0; slot < fakePlayer.getInventory().getContainerSize(); slot++) {
                if (fakePlayer.getInventory().getItem(slot).is(item)) {
                    return slot;
                }
            }
            return -1;
        }

        private void equipBestArmor(EntityPlayerMPFake fakePlayer) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                ArmorEvaluation current = ArmorEvaluation.forStack(fakePlayer.getItemBySlot(slot), slot);
                int bestSlot = -1;
                ArmorEvaluation bestCandidate = current;
                for (int inventorySlot = 0; inventorySlot < fakePlayer.getInventory().getContainerSize(); inventorySlot++) {
                    ArmorEvaluation candidate = ArmorEvaluation.forStack(fakePlayer.getInventory().getItem(inventorySlot), slot);
                    if (candidate.isBetterThan(bestCandidate)) {
                        bestCandidate = candidate;
                        bestSlot = inventorySlot;
                    }
                }
                if (bestSlot >= 0) {
                    swapInventoryWithEquipment(fakePlayer, bestSlot, slot);
                }
            }
        }

        private void equipBestWeapon(EntityPlayerMPFake fakePlayer) {
            WeaponEvaluation current = WeaponEvaluation.forStack(fakePlayer.getMainHandItem());
            int bestSlot = -1;
            WeaponEvaluation bestCandidate = current;
            for (int inventorySlot = 0; inventorySlot < fakePlayer.getInventory().getContainerSize(); inventorySlot++) {
                WeaponEvaluation candidate = WeaponEvaluation.forStack(fakePlayer.getInventory().getItem(inventorySlot));
                if (candidate.isBetterThan(bestCandidate)) {
                    bestCandidate = candidate;
                    bestSlot = inventorySlot;
                }
            }
            if (bestSlot >= 0) {
                swapInventoryWithEquipment(fakePlayer, bestSlot, EquipmentSlot.MAINHAND);
            }
        }

        private void swapInventoryWithEquipment(EntityPlayerMPFake fakePlayer, int inventorySlot, EquipmentSlot equipmentSlot) {
            ItemStack inventoryStack = fakePlayer.getInventory().getItem(inventorySlot).copy();
            ItemStack equippedStack = fakePlayer.getItemBySlot(equipmentSlot).copy();
            fakePlayer.setItemSlot(equipmentSlot, inventoryStack);
            fakePlayer.getInventory().setItem(inventorySlot, equippedStack);
        }

        private HealingChoice findBestHealingChoice(EntityPlayerMPFake fakePlayer) {
            HealingChoice best = null;
            best = betterHealingChoice(best, HealingChoice.forStack(fakePlayer.getOffhandItem(), -2));
            for (int slot = 0; slot < fakePlayer.getInventory().getContainerSize(); slot++) {
                best = betterHealingChoice(best, HealingChoice.forStack(fakePlayer.getInventory().getItem(slot), slot));
            }
            return best;
        }

        private HealingChoice betterHealingChoice(HealingChoice currentBest, HealingChoice candidate) {
            if (candidate == null) {
                return currentBest;
            }
            if (currentBest == null || candidate.score() > currentBest.score()) {
                return candidate;
            }
            return currentBest;
        }

        private boolean beginHealingSequence(EntityPlayerMPFake fakePlayer, BotBrain brain, HealingChoice healingChoice, boolean underThreat) {
            if (brain.scriptedUse != null) {
                return true;
            }
            ScriptedUseKind useKind = resolveHealingUseKind(fakePlayer, healingChoice);
            InteractionHand hand = prepareHealingHand(fakePlayer, healingChoice);
            ItemStack stack = hand == InteractionHand.OFF_HAND ? fakePlayer.getOffhandItem() : fakePlayer.getMainHandItem();
            if (stack.isEmpty()) {
                return false;
            }
            brain.scriptedUse = new ScriptedUseState(
                    useKind,
                    hand,
                    stack.getCount(),
                    underThreat ? 10 : 6,
                    80
            );
            return true;
        }

        private ScriptedUseKind resolveHealingUseKind(EntityPlayerMPFake fakePlayer, HealingChoice healingChoice) {
            ItemStack stack = healingChoice.slot() == -2
                    ? fakePlayer.getOffhandItem()
                    : fakePlayer.getInventory().getItem(healingChoice.slot());
            if (stack.isEmpty()) {
                return ScriptedUseKind.EAT;
            }
            return switch (healingChoice.kind()) {
                case FOOD -> ScriptedUseKind.EAT;
                case POTION -> (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
                        ? ScriptedUseKind.THROW_POTION
                        : ScriptedUseKind.DRINK;
            };
        }

        private boolean tickScriptedUse(EntityPlayerMPFake fakePlayer, BotBrain brain, LivingEntity threat) {
            ScriptedUseState state = brain.scriptedUse;
            if (state == null) {
                return false;
            }
            if (state.timeoutTicks <= 0) {
                finishScriptedUse(fakePlayer, brain, false);
                return false;
            }
            state.timeoutTicks--;

            EntityPlayerActionPack actionPack = actionPack(fakePlayer);
            switch (state.kind) {
                case THROW_POTION -> {
                    if (state.phaseTicks > 4) {
                        if (threat != null) {
                            moveAwayFromThreat(fakePlayer, threat, 0.85F, brain);
                        } else {
                            actionPack.setForward(-0.65F);
                            actionPack.setStrafing(0.0F);
                            actionPack.setSneaking(false);
                            actionPack.setSprinting(false);
                        }
                    } else {
                        actionPack.setForward(0.0F);
                        actionPack.setStrafing(0.0F);
                        actionPack.setSneaking(true);
                        actionPack.setSprinting(false);
                        actionPack.look(fakePlayer.getYRot(), 70.0F);
                    }
                    if (state.phaseTicks == 4) {
                        actionPack.start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.once());
                    }
                    state.phaseTicks--;
                    if (state.phaseTicks <= 0) {
                        finishScriptedUse(fakePlayer, brain, true);
                    }
                    return true;
                }
                case EAT, DRINK -> {
                    if (state.phaseTicks > 0) {
                        if (threat != null) {
                            moveAwayFromThreat(fakePlayer, threat, 0.7F, brain);
                        } else {
                            actionPack.setForward(-0.45F);
                            actionPack.setStrafing(0.0F);
                            actionPack.setSneaking(false);
                            actionPack.setSprinting(false);
                        }
                        state.phaseTicks--;
                        if (state.phaseTicks == 0) {
                            actionPack.start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.continuous());
                        }
                        return true;
                    }

                    actionPack.setForward(0.0F);
                    actionPack.setStrafing(0.0F);
                    actionPack.setSneaking(false);
                    actionPack.setSprinting(false);
                    if (state.kind == ScriptedUseKind.EAT) {
                        actionPack.look(fakePlayer.getYRot(), 12.0F);
                    } else {
                        actionPack.look(fakePlayer.getYRot(), 2.0F);
                    }
                    if (hasConsumedHealingItem(fakePlayer, state) || (!fakePlayer.isUsingItem() && state.timeoutTicks < 70)) {
                        finishScriptedUse(fakePlayer, brain, true);
                    }
                    return true;
                }
            }
            return false;
        }

        private boolean hasConsumedHealingItem(EntityPlayerMPFake fakePlayer, ScriptedUseState state) {
            ItemStack current = state.hand == InteractionHand.OFF_HAND ? fakePlayer.getOffhandItem() : fakePlayer.getMainHandItem();
            return current.isEmpty() || current.getCount() < state.initialCount;
        }

        private void finishScriptedUse(EntityPlayerMPFake fakePlayer, BotBrain brain, boolean appliedCooldown) {
            stopActionMovement(fakePlayer, true);
            if (appliedCooldown && brain.scriptedUse != null) {
                brain.healCooldownTicks = brain.scriptedUse.kind == ScriptedUseKind.THROW_POTION ? 16 : 24;
            }
            brain.scriptedUse = null;
        }

        private InteractionHand prepareHealingHand(EntityPlayerMPFake fakePlayer, HealingChoice healingChoice) {
            if (healingChoice.slot() == -2) {
                return InteractionHand.OFF_HAND;
            }
            EntityPlayerActionPack actionPack = actionPack(fakePlayer);
            int slot = healingChoice.slot();
            if (slot >= 0 && slot <= 8) {
                actionPack.setSlot(slot + 1);
                return InteractionHand.MAIN_HAND;
            }
            int selectedSlot = fakePlayer.getInventory().getSelectedSlot();
            ItemStack selectedStack = fakePlayer.getInventory().getItem(selectedSlot).copy();
            ItemStack healingStack = fakePlayer.getInventory().getItem(slot).copy();
            fakePlayer.getInventory().setItem(selectedSlot, healingStack);
            fakePlayer.getInventory().setItem(slot, selectedStack);
            actionPack.setSlot(selectedSlot + 1);
            return InteractionHand.MAIN_HAND;
        }

        private boolean consumeFoodHealing(EntityPlayerMPFake fakePlayer, ItemStack stack, HealingChoice healingChoice) {
            stack.shrink(1);
            fakePlayer.heal(healingChoice.healAmount());
            applySpecialFoodEffects(fakePlayer, healingChoice.itemId());
            return true;
        }

        private boolean consumePotionHealing(EntityPlayerMPFake fakePlayer, ItemStack stack, HealingChoice healingChoice) {
            stack.shrink(1);
            if (healingChoice.healAmount() > 0.0F) {
                fakePlayer.heal(healingChoice.healAmount());
            }
            if (healingChoice.regenerationDurationTicks() > 0) {
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, healingChoice.regenerationDurationTicks(), healingChoice.regenerationAmplifier()));
            }
            return true;
        }

        private void applySpecialFoodEffects(EntityPlayerMPFake fakePlayer, String itemId) {
            if (itemId.endsWith("golden_apple")) {
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
            } else if (itemId.endsWith("enchanted_golden_apple")) {
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6000, 0));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
            }
        }

        private void tryAttackTarget(EntityPlayerMPFake source, LivingEntity target, BotBrain brain, CombatPresetSpec combatPreset) {
            double reach = combatPreset == null ? 3.0D : combatPreset.reach();
            if (source.distanceToSqr(target) > reach * reach) {
                return;
            }
            if (source.getAttackStrengthScale(0.5F) < 0.92F) {
                return;
            }
            source.swing(InteractionHand.MAIN_HAND, true);
            if (combatPreset != null && (!combatPreset.damageEnabled() || !combatPreset.fakeHitEnabled())) {
                applyFakeHit(source, target);
            } else {
                source.attack(target);
            }
            source.resetAttackStrengthTicker();
            if (combatPreset != null && combatPreset.stapEnabled()) {
                brain.attackRetreatTicks = 9;
                moveAwayFromThreat(source, target, 1.0F, brain);
            }
            if (combatPreset != null && combatPreset.flex360Enabled()) {
                brain.flexSpinTicks = FLEX_SPIN_STEPS;
            }
        }

        private void applyFakeHit(EntityPlayerMPFake source, LivingEntity target) {
            Vec3 push = target.position().subtract(source.position());
            Vec3 horizontal = new Vec3(push.x, 0.0D, push.z);
            if (horizontal.lengthSqr() < 0.0001D) {
                horizontal = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 normalized = horizontal.normalize();
            target.knockback(0.45D, -normalized.x, -normalized.z);
        }

        private boolean shouldJumpTowardTarget(EntityPlayerMPFake source, Entity target, Vec3 motion, BotBrain brain) {
            BlockPos stepPos = BlockPos.containing(source.getX() + motion.x * 1.2D, source.getY(), source.getZ() + motion.z * 1.2D);
            boolean stepBlocked = source.level().getBlockState(stepPos).blocksMotion()
                    && !source.level().getBlockState(stepPos.above()).blocksMotion();
            if (stepBlocked) {
                return true;
            }
            if (target.getY() > source.getY() + 0.75D) {
                return true;
            }
            return brain.stuckTicks >= 8;
        }

        private boolean shouldSneakTowardTarget(EntityPlayerMPFake source, Entity target, Vec3 motion) {
            BlockPos aheadBelow = BlockPos.containing(source.getX() + motion.x * 1.1D, source.getY() - 1.0D, source.getZ() + motion.z * 1.1D);
            if (!source.level().getBlockState(aheadBelow).isAir()) {
                return false;
            }
            if (target.getY() < source.getY() - 0.4D) {
                return false;
            }
            for (int fall = 2; fall <= 4; fall++) {
                if (!source.level().getBlockState(aheadBelow.below(fall - 1)).isAir()) {
                    return false;
                }
            }
            return true;
        }

        private void updateStuckState(EntityPlayerMPFake source, BotBrain brain, boolean wantsMovement, Entity target) {
            if (!wantsMovement) {
                brain.stuckTicks = 0;
                brain.unstuckStrafeTicks = 0;
                brain.lastTrackedPosition = source.position();
                return;
            }
            if (brain.lastTrackedPosition != null && source.position().distanceToSqr(brain.lastTrackedPosition) < 0.01D) {
                brain.stuckTicks++;
            } else {
                brain.stuckTicks = 0;
            }
            if (brain.unstuckStrafeTicks > 0) {
                brain.unstuckStrafeTicks--;
            } else if (brain.stuckTicks >= 8) {
                brain.unstuckStrafeDirection *= -1.0F;
                brain.unstuckStrafeTicks = 8;
                if (target != null) {
                    lookAtTarget(source, target);
                }
            }
            brain.lastTrackedPosition = source.position();
        }

        private double horizontalDistance(EntityPlayerMPFake source, Entity target) {
            double dx = target.getX() - source.getX();
            double dz = target.getZ() - source.getZ();
            return Math.sqrt(dx * dx + dz * dz);
        }

        private void debug(String pattern, Object... args) {
            if (PlayerBatchConfig.isDebugEnabled()) {
                PlayerBatch.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }
    }

    private static final class SummonBatch {
        private static final int POST_SPAWN_SETTLE_TICKS = 20;
        private final ServerState owner;
        private final MinecraftServer server;
        private final CommandSourceStack feedbackSource;
        private final CommandSourceStack executionSource;
        private final Deque<SummonEntry> remainingEntries;
        private final List<String> batchNames;
        private final Map<String, BotConfig> plannedConfigs;
        private final Set<String> pendingTagNames = new LinkedHashSet<>();
        private final Map<String, BotConfig> pendingConfigs = new LinkedHashMap<>();
        private final int totalCount;
        private final String formation;
        private final BotConfig config;
        private final ServerBossEvent bossBar;
        private boolean started;
        private boolean awaitingPostSpawnApply;
        private int settleTicksRemaining = POST_SPAWN_SETTLE_TICKS;
        private int successCount;
        private int failCount;

        private SummonBatch(ServerState owner, MinecraftServer server, CommandSourceStack source, List<String> names, BotConfig config) {
            this.owner = owner;
            this.server = server;
            this.feedbackSource = source;
            this.executionSource = source.withSuppressedOutput();
            this.config = config;
            this.formation = config.formation();
            this.batchNames = List.copyOf(names);
            this.plannedConfigs = buildPlannedConfigs(names, config);
            this.remainingEntries = new ArrayDeque<>(buildFormationEntries(source, names, formation));
            this.totalCount = names.size();
            this.bossBar = new ServerBossEvent(
                    Component.literal("Summoning Bots..."),
                    BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            this.bossBar.setProgress(0.0F);
        }

        private void start() {
            if (started) {
                return;
            }
            started = true;
            if (feedbackSource.getPlayer() != null) {
                bossBar.addPlayer(feedbackSource.getPlayer());
            }
            bossBar.setVisible(true);
        }

        private void tick(int maxSpawnsPerTick) {
            if (!awaitingPostSpawnApply) {
                int processedThisTick = Math.max(1, maxSpawnsPerTick);
                for (int index = 0; index < processedThisTick && !remainingEntries.isEmpty(); index++) {
                    summonNext();
                }
                if (remainingEntries.isEmpty()) {
                    awaitingPostSpawnApply = true;
                }
            } else if (!pendingTagNames.isEmpty() && settleTicksRemaining > 0) {
                settleTicksRemaining--;
            }
            updateBossBar();
        }

        private void summonNext() {
            SummonEntry nextEntry = remainingEntries.removeFirst();
            String nextName = nextEntry.name();
            String command = nextEntry.command();
            try {
                boolean accepted = CommandCompat.performPrefixedCommand(executionSource, command);
                ServerPlayer spawnedPlayer = server.getPlayerList().getPlayerByName(nextName);
                if (accepted || spawnedPlayer != null) {
                    owner.markManagedName(nextName);
                    successCount++;
                    BotConfig spawnConfig = plannedConfigs.getOrDefault(nextName, baseConfig());
                    if (spawnedPlayer instanceof EntityPlayerMPFake fakePlayer) {
                        owner.markManagedBot(fakePlayer);
                    }
                    pendingTagNames.add(nextName);
                    pendingConfigs.put(nextName, spawnConfig);
                    debug("Spawn accepted for {} in {} formation", nextName, formation);
                } else {
                    failCount++;
                    PlayerBatch.LOGGER.warn("Spawn rejected for fake player {}", nextName);
                }
            } catch (Exception exception) {
                failCount++;
                PlayerBatch.LOGGER.error("Failed to execute Carpet summon command for {}", nextName, exception);
            }
        }

        private void tryTagKnownPlayers() {
            List<String> tagged = new ArrayList<>();
            for (String pendingName : pendingTagNames) {
                ServerPlayer queuedPlayer = server.getPlayerList().getPlayerByName(pendingName);
                if (queuedPlayer instanceof EntityPlayerMPFake fakePlayer) {
                    owner.markManagedBot(fakePlayer);
                    tagged.add(pendingName);
                }
            }
            pendingTagNames.removeAll(tagged);
        }

        private void collectReservedNames(Collection<String> reserved) {
            for (SummonEntry entry : remainingEntries) {
                reserved.add(entry.name().toLowerCase(Locale.ROOT));
            }
            for (String pendingTagName : pendingTagNames) {
                reserved.add(pendingTagName.toLowerCase(Locale.ROOT));
            }
        }

        private BatchProgress progress() {
            return new BatchProgress(true, totalCount, successCount, failCount, remainingEntries.size());
        }

        private boolean isComplete() {
            return awaitingPostSpawnApply && (pendingTagNames.isEmpty() || settleTicksRemaining <= 0);
        }

        private void finish() {
            tagAllBatchBotsOnce();
            applyDeferredBatchConfiguration();
            bossBar.removeAllPlayers();
            feedbackSource.sendSuccess(
                    () -> Component.literal(
                            "Summoned " + successCount + "/" + totalCount + " bots (" + failCount + " failed)"
                    ).withStyle(failCount > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                    true
            );
        }

        private void tagAllBatchBotsOnce() {
            for (String batchName : batchNames) {
                ServerPlayer player = server.getPlayerList().getPlayerByName(batchName);
                if (player instanceof EntityPlayerMPFake fakePlayer) {
                    owner.markManagedBot(fakePlayer);
                    pendingTagNames.remove(batchName);
                }
            }
            tryTagKnownPlayers();
        }

        private void applyDeferredBatchConfiguration() {
            for (String batchName : batchNames) {
                ServerPlayer player = server.getPlayerList().getPlayerByName(batchName);
                if (player instanceof EntityPlayerMPFake fakePlayer) {
                    owner.clearSelectionState(fakePlayer);
                    cleanupManagedBot(fakePlayer);
                    owner.markManagedBot(fakePlayer);
                    applyConfig(fakePlayer, plannedConfigs.getOrDefault(batchName, baseConfig()));
                }
            }
            pendingTagNames.clear();
            pendingConfigs.clear();
        }

        private void applyConfig(EntityPlayerMPFake fakePlayer, BotConfig appliedConfig) {
            var gameRules = fakePlayer.level().getGameRules();
            boolean previous = gameRules.get(GameRules.SHOW_ADVANCEMENT_MESSAGES);
            if (previous) {
                gameRules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
            }
            try {
                fakePlayer.setGameMode(GameType.SURVIVAL);
                appliedConfig.loadout().applyTo(fakePlayer);
                owner.applyCombatPreset(fakePlayer, appliedConfig.combatPreset());
            } finally {
                if (previous) {
                    gameRules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, true, server);
                }
            }
        }

        private BotConfig baseConfig() {
            return new BotConfig(config.formation(), config.loadout(), config.combatPreset());
        }

        private Map<String, BotConfig> buildPlannedConfigs(List<String> names, BotConfig batchConfig) {
            Map<String, BotConfig> result = new LinkedHashMap<>();
            BotConfig baseConfig = baseConfig();
            for (String name : names) {
                result.put(name, baseConfig);
            }
            if (batchConfig.distributions().isEmpty()) {
                return result;
            }

            List<BotConfig.DistributionRule> rules = batchConfig.distributions().stream()
                    .filter(rule -> rule.percent() > 0 && !rule.loadout().isEmpty())
                    .toList();
            if (rules.isEmpty()) {
                return result;
            }

            List<Integer> counts = allocateDistributionCounts(names.size(), rules);
            List<String> allocationOrder = new ArrayList<>(names);
            Collections.shuffle(allocationOrder, new Random(allocationOrder.hashCode()));
            int cursor = 0;
            for (int index = 0; index < rules.size(); index++) {
                BotConfig.DistributionRule rule = rules.get(index);
                int ruleCount = counts.get(index);
                BotConfig distributedConfig = new BotConfig(
                        batchConfig.formation(),
                        batchConfig.loadout().mergedWith(rule.loadout()),
                        batchConfig.combatPreset()
                );
                for (int assigned = 0; assigned < ruleCount && cursor < allocationOrder.size(); assigned++, cursor++) {
                    result.put(allocationOrder.get(cursor), distributedConfig);
                }
            }
            return result;
        }

        private List<Integer> allocateDistributionCounts(int totalBots, List<BotConfig.DistributionRule> rules) {
            List<Integer> counts = new ArrayList<>();
            List<Double> remainders = new ArrayList<>();
            int assigned = 0;
            for (BotConfig.DistributionRule rule : rules) {
                double exact = totalBots * (rule.percent() / 100.0D);
                int floor = (int) Math.floor(exact);
                counts.add(floor);
                remainders.add(exact - floor);
                assigned += floor;
            }
            int targetAssigned = Math.min(totalBots, (int) Math.round(totalBots * (rules.stream().mapToInt(BotConfig.DistributionRule::percent).sum() / 100.0D)));
            while (assigned < targetAssigned) {
                int bestIndex = -1;
                double bestRemainder = -1.0D;
                for (int index = 0; index < remainders.size(); index++) {
                    if (remainders.get(index) > bestRemainder) {
                        bestRemainder = remainders.get(index);
                        bestIndex = index;
                    }
                }
                if (bestIndex < 0) {
                    break;
                }
                counts.set(bestIndex, counts.get(bestIndex) + 1);
                remainders.set(bestIndex, 0.0D);
                assigned++;
            }
            return counts;
        }

        private void discard() {
            bossBar.removeAllPlayers();
        }

        private void updateBossBar() {
            float progress = totalCount <= 0 ? 1.0F : (float) (successCount + failCount) / (float) totalCount;
            bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
            bossBar.setName(Component.literal("Summoning Bots... " + (successCount + failCount) + "/" + totalCount));
        }

        private void debug(String pattern, Object... args) {
            if (PlayerBatchConfig.isDebugEnabled()) {
                PlayerBatch.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }

        private List<SummonEntry> buildFormationEntries(CommandSourceStack source, List<String> names, String formation) {
            String normalizedFormation = normalizeFormation(formation);
            if (normalizedFormation == null) {
                return buildCircleEntries(source, names);
            }
            return switch (normalizedFormation) {
                case "square" -> buildSquareEntries(source, names);
                case "triangle" -> buildTriangleEntries(source, names);
                case "random" -> buildRandomEntries(source, names);
                case "single block" -> buildSingleBlockEntries(source, names);
                case "circle" -> buildCircleEntries(source, names);
                default -> buildCircleEntries(source, names);
            };
        }

        private List<SummonEntry> buildCircleEntries(CommandSourceStack source, List<String> names) {
            List<SummonEntry> entries = new ArrayList<>(names.size());
            Vec3 center = source.getPosition();
            ServerLevel level = source.getLevel();
            double radius = names.size() <= 1 ? 0.0D : Math.max(2.5D, names.size() / (2.0D * Math.PI));
            for (int index = 0; index < names.size(); index++) {
                double angle = names.size() <= 1 ? 0.0D : (Math.PI * 2.0D * index) / names.size();
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                int blockX = BlockPos.containing(x, center.y, z).getX();
                int blockZ = BlockPos.containing(x, center.y, z).getZ();
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
                double y = topY;
                String command = String.format(Locale.ROOT, "player %s spawn at %.3f %.3f %.3f", names.get(index), x, y, z);
                entries.add(new SummonEntry(names.get(index), command));
            }
            return entries;
        }

        private List<SummonEntry> buildSquareEntries(CommandSourceStack source, List<String> names) {
            List<SummonEntry> entries = new ArrayList<>(names.size());
            Vec3 center = source.getPosition();
            ServerLevel level = source.getLevel();
            int sideLength = Math.max(1, (int) Math.ceil(Math.sqrt(names.size())));
            double spacing = 2.0D;
            double originX = center.x - ((sideLength - 1) * spacing) / 2.0D;
            double originZ = center.z - ((sideLength - 1) * spacing) / 2.0D;
            for (int index = 0; index < names.size(); index++) {
                int row = index / sideLength;
                int column = index % sideLength;
                double x = originX + (column * spacing);
                double z = originZ + (row * spacing);
                entries.add(buildEntry(level, center, names.get(index), x, z));
            }
            return entries;
        }

        private List<SummonEntry> buildTriangleEntries(CommandSourceStack source, List<String> names) {
            List<SummonEntry> entries = new ArrayList<>(names.size());
            Vec3 center = source.getPosition();
            ServerLevel level = source.getLevel();
            double spacing = 2.0D;
            int placed = 0;
            for (int row = 0; placed < names.size(); row++) {
                int rowCount = row + 1;
                double z = center.z + (row * spacing) - (spacing * Math.max(0, names.size() / 6.0D));
                double startX = center.x - ((rowCount - 1) * spacing) / 2.0D;
                for (int column = 0; column < rowCount && placed < names.size(); column++) {
                    double x = startX + (column * spacing);
                    entries.add(buildEntry(level, center, names.get(placed), x, z));
                    placed++;
                }
            }
            return entries;
        }

        private List<SummonEntry> buildRandomEntries(CommandSourceStack source, List<String> names) {
            List<SummonEntry> entries = new ArrayList<>(names.size());
            Vec3 center = source.getPosition();
            ServerLevel level = source.getLevel();
            double radius = Math.max(2.5D, Math.sqrt(names.size()) * 1.8D);
            for (int index = 0; index < names.size(); index++) {
                double angle = (Math.PI * 2.0D * index) / Math.max(1, names.size());
                double distance = ((index * 1103515245L + 12345L) & 0x7fffffffL) / (double) Integer.MAX_VALUE;
                double spread = 0.35D + (distance * 0.65D);
                double x = center.x + Math.cos(angle * 1.7D) * radius * spread;
                double z = center.z + Math.sin(angle * 1.3D) * radius * spread;
                entries.add(buildEntry(level, center, names.get(index), x, z));
            }
            return entries;
        }

        private List<SummonEntry> buildSingleBlockEntries(CommandSourceStack source, List<String> names) {
            List<SummonEntry> entries = new ArrayList<>(names.size());
            Vec3 center = source.getPosition();
            ServerLevel level = source.getLevel();
            int blockX = BlockPos.containing(center).getX();
            int blockZ = BlockPos.containing(center).getZ();
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            for (String name : names) {
                String command = String.format(Locale.ROOT, "player %s spawn at %.3f %.3f %.3f", name, blockX + 0.5D, (double) topY, blockZ + 0.5D);
                entries.add(new SummonEntry(name, command));
            }
            return entries;
        }

        private SummonEntry buildEntry(ServerLevel level, Vec3 center, String name, double x, double z) {
            int blockX = BlockPos.containing(x, center.y, z).getX();
            int blockZ = BlockPos.containing(x, center.y, z).getZ();
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            String command = String.format(Locale.ROOT, "player %s spawn at %.3f %.3f %.3f", name, x, (double) topY, z);
            return new SummonEntry(name, command);
        }

        private record SummonEntry(String name, String command) {
        }
    }

    private static void applySelectionGlow(EntityPlayerMPFake player) {
        player.setGlowingTag(true);
        player.addEffect(new MobEffectInstance(SELECTED_GLOWING));
    }

    private static void removeSelectionGlow(EntityPlayerMPFake player) {
        player.setGlowingTag(false);
        player.removeEffect(MobEffects.GLOWING);
    }

    private static List<BlockPos> findNearestBlocks(ServerLevel level, Vec3 center, String blockName, int needed) {
        String targetBlock = normalizeBlockId(blockName);
        Set<BlockPos> used = new HashSet<>();
        List<BlockPos> matches = new ArrayList<>();
        BlockPos centerPos = BlockPos.containing(center);
        int centerY = centerPos.getY();

        for (int radius = 0; radius <= 256 && matches.size() < needed; radius++) {
            for (int x = centerPos.getX() - radius; x <= centerPos.getX() + radius && matches.size() < needed; x++) {
                for (int z = centerPos.getZ() - radius; z <= centerPos.getZ() + radius && matches.size() < needed; z++) {
                    if (Math.max(Math.abs(x - centerPos.getX()), Math.abs(z - centerPos.getZ())) != radius) {
                        continue;
                    }
                    if (!level.hasChunkAt(new BlockPos(x, centerY, z))) {
                        continue;
                    }
                    scanColumn(level, x, z, centerY, targetBlock, needed, used, matches);
                }
            }
        }
        return matches;
    }

    private static void scanColumn(
            ServerLevel level,
            int x,
            int z,
            int centerY,
            String targetBlock,
            int needed,
            Set<BlockPos> used,
            List<BlockPos> matches
    ) {
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;
        for (int delta = 0; delta <= Math.max(centerY - minY, maxY - centerY) && matches.size() < needed; delta++) {
            int upY = centerY + delta;
            int downY = centerY - delta;
            if (upY <= maxY) {
                collectMatch(level, x, upY, z, targetBlock, used, matches);
            }
            if (delta > 0 && downY >= minY && matches.size() < needed) {
                collectMatch(level, x, downY, z, targetBlock, used, matches);
            }
        }
    }

    private static void collectMatch(
            ServerLevel level,
            int x,
            int y,
            int z,
            String targetBlock,
            Set<BlockPos> used,
            Collection<BlockPos> matches
    ) {
        BlockPos pos = new BlockPos(x, y, z);
        if (used.contains(pos)) {
            return;
        }
        String blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (blockKey.equals(targetBlock)) {
            used.add(pos);
            matches.add(pos.immutable());
        }
    }

    private static Vec3 averagePosition(List<EntityPlayerMPFake> players) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (EntityPlayerMPFake player : players) {
            x += player.getX();
            y += player.getY();
            z += player.getZ();
        }
        int count = Math.max(1, players.size());
        return new Vec3(x / count, y / count, z / count);
    }

    private static String normalizeGroupName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32 || !trimmed.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFormation(String rawFormation) {
        if (rawFormation == null || rawFormation.isBlank()) {
            return DEFAULT_FORMATION;
        }
        String normalized = rawFormation.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "circle", "square", "triangle", "random", "single block" -> normalized;
            case "single_block", "singleblock" -> "single block";
            default -> null;
        };
    }

    private static String makeUniqueSummonName(String requestedName, Collection<String> reservedLowercaseNames) {
        String baseName = requestedName == null || requestedName.isBlank() ? "PlayerBatch" : requestedName.trim();
        String normalizedBase = baseName.toLowerCase(Locale.ROOT);
        if (!reservedLowercaseNames.contains(normalizedBase)) {
            return baseName;
        }

        String prefix = baseName.length() > 12 ? baseName.substring(0, 12) : baseName;
        for (int suffixNumber = 2; suffixNumber < 10_000; suffixNumber++) {
            String candidate = prefix + suffixNumber;
            if (candidate.length() > 16) {
                int digits = Integer.toString(suffixNumber).length();
                int maxPrefixLength = Math.max(1, 16 - digits);
                candidate = prefix.substring(0, Math.min(prefix.length(), maxPrefixLength)) + suffixNumber;
            }
            String loweredCandidate = candidate.toLowerCase(Locale.ROOT);
            if (!reservedLowercaseNames.contains(loweredCandidate)) {
                return candidate;
            }
        }
        return "PB" + Math.abs(baseName.hashCode() % 1_000_000);
    }

    private static boolean matchesArmorSlot(String itemId, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> itemId.endsWith("_helmet");
            case CHEST -> itemId.endsWith("_chestplate");
            case LEGS -> itemId.endsWith("_leggings");
            case FEET -> itemId.endsWith("_boots");
            default -> false;
        };
    }

    private static int armorMaterialScore(String itemId) {
        if (itemId.contains("netherite")) {
            return 5;
        }
        if (itemId.contains("diamond")) {
            return 4;
        }
        if (itemId.contains("golden")) {
            return 3;
        }
        if (itemId.contains("iron")) {
            return 2;
        }
        if (itemId.contains("leather")) {
            return 1;
        }
        return -1;
    }

    private static int toolMaterialScore(String itemId) {
        if (itemId.contains("netherite")) {
            return 6;
        }
        if (itemId.contains("diamond")) {
            return 5;
        }
        if (itemId.contains("golden")) {
            return 4;
        }
        if (itemId.contains("iron")) {
            return 3;
        }
        if (itemId.contains("stone")) {
            return 2;
        }
        if (itemId.contains("wooden")) {
            return 1;
        }
        return -1;
    }

    private static String suffix(int count) {
        return count == 1 ? "" : "s";
    }

    private record GroupResult(boolean success, String message, int affected) {
        private static GroupResult success(String message, int affected) {
            return new GroupResult(true, message, affected);
        }

        private static GroupResult failure(String message) {
            return new GroupResult(false, message, 0);
        }
    }

    private record AiResult(boolean success, String message, int affected) {
        private static AiResult success(String message, int affected) {
            return new AiResult(true, message, affected);
        }

        private static AiResult failure(String message) {
            return new AiResult(false, message, 0);
        }
    }

    private static final class BotGroup {
        private final String displayName;
        private final Set<UUID> memberIds = new LinkedHashSet<>();
        private EnumSet<BotAiMode> sharedModes = EnumSet.of(BotAiMode.IDLE);

        private BotGroup(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private Set<UUID> memberIds() {
            return memberIds;
        }

        private EnumSet<BotAiMode> sharedModes() {
            return sharedModes;
        }
    }

    private static final class BotBrain {
        private EnumSet<BotAiMode> modes = EnumSet.of(BotAiMode.IDLE);
        private String groupKey;
        private CombatPresetSpec combatPreset;
        private int healCooldownTicks;
        private int attackRetreatTicks;
        private int flexSpinTicks;
        private int flexSpinDirection = 1;
        private float flexSpinProgress;
        private int stuckTicks;
        private int unstuckStrafeTicks;
        private float unstuckStrafeDirection = 1.0F;
        private Vec3 lastTrackedPosition;
        private ScriptedUseState scriptedUse;
    }

    private enum ScriptedUseKind {
        EAT,
        DRINK,
        THROW_POTION
    }

    private static final class ScriptedUseState {
        private final ScriptedUseKind kind;
        private final InteractionHand hand;
        private final int initialCount;
        private int phaseTicks;
        private int timeoutTicks;

        private ScriptedUseState(
                ScriptedUseKind kind,
                InteractionHand hand,
                int initialCount,
                int phaseTicks,
                int timeoutTicks
        ) {
            this.kind = kind;
            this.hand = hand;
            this.initialCount = initialCount;
            this.phaseTicks = phaseTicks;
            this.timeoutTicks = timeoutTicks;
        }
    }

    private record ArmorEvaluation(int materialScore, boolean enchanted, ItemStack stack) {
        private static ArmorEvaluation forStack(ItemStack stack, EquipmentSlot targetSlot) {
            if (stack == null || stack.isEmpty()) {
                return new ArmorEvaluation(-1, false, ItemStack.EMPTY);
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!matchesArmorSlot(itemId, targetSlot)) {
                return new ArmorEvaluation(-1, false, stack);
            }
            return new ArmorEvaluation(armorMaterialScore(itemId), stack.isEnchanted(), stack);
        }

        private boolean isBetterThan(ArmorEvaluation other) {
            if (materialScore != other.materialScore) {
                return materialScore > other.materialScore;
            }
            return materialScore >= 0 && enchanted && !other.enchanted;
        }
    }

    private record WeaponEvaluation(int materialScore, int typeScore, boolean enchanted, ItemStack stack) {
        private static WeaponEvaluation forStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return new WeaponEvaluation(-1, -1, false, ItemStack.EMPTY);
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int typeScore = itemId.endsWith("_sword") ? 2 : itemId.endsWith("_axe") ? 1 : -1;
            if (typeScore < 0) {
                return new WeaponEvaluation(-1, -1, false, stack);
            }
            return new WeaponEvaluation(toolMaterialScore(itemId), typeScore, stack.isEnchanted(), stack);
        }

        private boolean isBetterThan(WeaponEvaluation other) {
            if (materialScore != other.materialScore) {
                return materialScore > other.materialScore;
            }
            if (materialScore >= 0 && enchanted != other.enchanted) {
                return enchanted;
            }
            return typeScore > other.typeScore;
        }
    }

    private record HealingChoice(int slot, Kind kind, float score, float healAmount, int regenerationDurationTicks, int regenerationAmplifier, String itemId) {
        private static HealingChoice forStack(ItemStack stack, int slot) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null && potionContents.potion().isPresent()) {
                String potionId = BuiltInRegistries.POTION.getKey(potionContents.potion().get().value()).toString();
                if (potionId.contains("healing")) {
                    float healAmount = potionId.contains("strong_") ? 8.0F : 4.0F;
                    return new HealingChoice(slot, Kind.POTION, healAmount * 3.0F, healAmount, 0, 0, potionId);
                }
                if (potionId.contains("regeneration")) {
                    int duration = potionId.contains("long_") ? 1800 : 900;
                    int amplifier = potionId.contains("strong_") ? 1 : 0;
                    float score = potionId.contains("strong_") ? 10.0F : potionId.contains("long_") ? 9.0F : 8.0F;
                    return new HealingChoice(slot, Kind.POTION, score, 0.0F, duration, amplifier, potionId);
                }
            }

            FoodProperties foodProperties = stack.get(DataComponents.FOOD);
            if (foodProperties != null) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                float baseHeal = foodProperties.nutrition();
                if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                    baseHeal += 20.0F;
                } else if (stack.is(Items.GOLDEN_APPLE)) {
                    baseHeal += 8.0F;
                }
                return new HealingChoice(slot, Kind.FOOD, baseHeal, Math.max(2.0F, baseHeal), 0, 0, itemId);
            }
            return null;
        }

        private enum Kind {
            FOOD,
            POTION
        }
    }

    public static void cleanupManagedBot(Entity entity) {
        if (entity instanceof EntityPlayerMPFake fakePlayer) {
            cleanupManagedBot(fakePlayer, true);
        }
    }

    private static void cleanupManagedBot(EntityPlayerMPFake fakePlayer) {
        cleanupManagedBot(fakePlayer, false);
    }

    private static void cleanupManagedBot(EntityPlayerMPFake fakePlayer, boolean preserveManagedTag) {
        fakePlayer.setGlowingTag(false);
        fakePlayer.removeAllEffects();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fakePlayer.setItemSlot(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < fakePlayer.getInventory().getContainerSize(); index++) {
            fakePlayer.getInventory().setItem(index, ItemStack.EMPTY);
        }
        for (String tag : List.copyOf(fakePlayer.getTags())) {
            if (preserveManagedTag && BOT_TAG.equals(tag)) {
                continue;
            }
            fakePlayer.removeTag(tag);
        }
        if (preserveManagedTag && !fakePlayer.getTags().contains(BOT_TAG)) {
            fakePlayer.addTag(BOT_TAG);
        }
    }
}

