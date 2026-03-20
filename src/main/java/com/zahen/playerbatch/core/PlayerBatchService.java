package com.zahen.playerbatch.core;

import carpet.patches.EntityPlayerMPFake;
import com.zahen.playerbatch.PlayerBatch;
import com.zahen.playerbatch.compat.CommandCompat;
import com.zahen.playerbatch.config.PlayerBatchConfig;
import com.zahen.playerbatch.item.SelectionWandItem;
import com.zahen.playerbatch.name.NamePlanner;
import com.zahen.playerbatch.network.PlayerBatchNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerBatchService {
    private static final String BOT_TAG = "bot";
    private static final int AI_TICK_INTERVAL = 10;
    private static final String DEFAULT_FORMATION = "circle";
    private static final ConcurrentMap<MinecraftServer, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final MobEffectInstance SELECTED_GLOWING = new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false);

    private PlayerBatchService() {
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames) {
        return requestSummon(source, count, rawNames, DEFAULT_FORMATION);
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames, String rawFormation) {
        if (source.getServer() == null) {
            return 0;
        }

        String formation = normalizeFormation(rawFormation);
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

            int queued = state(source.getServer()).queueBatch(source, plannedNames, formation);
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

    public static int openGui(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can open the PlayerBatch GUI."));
            return 0;
        }

        state(source.getServer()).addSubscriber(player.getUUID());
        PlayerBatchNetworking.sendState(player, snapshot(source.getServer(), true));
        return 1;
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
        BotAiMode mode = BotAiMode.fromString(rawMode);
        if (mode == null) {
            source.sendFailure(Component.literal("Unknown AI mode. Use: idle, combat, patrol, guard, follow, flee."));
            return 0;
        }
        AiResult result = state(source.getServer()).setSelectedAiMode(mode);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return result.affected();
    }

    public static int setGroupAiMode(CommandSourceStack source, String rawName, String rawMode) {
        BotAiMode mode = BotAiMode.fromString(rawMode);
        if (mode == null) {
            source.sendFailure(Component.literal("Unknown AI mode. Use: idle, combat, patrol, guard, follow, flee."));
            return 0;
        }
        AiResult result = state(source.getServer()).setGroupAiMode(rawName, mode);
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
        state(player.createCommandSourceStack().getServer()).removeSubscriber(player.getUUID());
    }

    public static void requestState(ServerPlayer player, boolean openScreen) {
        state(player.createCommandSourceStack().getServer()).addSubscriber(player.getUUID());
        PlayerBatchNetworking.sendState(player, snapshot(player.createCommandSourceStack().getServer(), openScreen));
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
        requestSummon(player.createCommandSourceStack(), count, rawNames, formation);
    }

    public static void runSelectedActionFromGui(ServerPlayer player, String action) {
        runSelectedAction(player.createCommandSourceStack(), action);
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
            queue.addLast(new SummonBatch(this, server, source, uniqueNames, formation));
            debug("Queued summon batch with {} names using {} formation", uniqueNames.size(), formation);
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

            if (aiTickCooldown-- <= 0) {
                tickAi();
                aiTickCooldown = AI_TICK_INTERVAL;
            }
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
                if (brain.mode != group.sharedMode()) {
                    brain.mode = group.sharedMode();
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
                summaries.add(group.displayName() + "=" + group.memberIds().size() + " [" + group.sharedMode().displayName() + "]");
            }
            return summaries;
        }

        private AiResult setSelectedAiMode(BotAiMode mode) {
            List<EntityPlayerMPFake> players = selectedPlayers();
            if (players.isEmpty()) {
                return AiResult.failure("Select one or more managed bots first.");
            }
            for (EntityPlayerMPFake player : players) {
                ensureBrain(player).mode = mode;
            }
            broadcast(false);
            return AiResult.success("Set AI mode to '" + mode.displayName() + "' for " + players.size() + " selected bot" + suffix(players.size()) + ".", players.size());
        }

        private AiResult setGroupAiMode(String rawName, BotAiMode mode) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return AiResult.failure("Unknown group: " + rawName);
            }
            group.sharedMode = mode;
            int affected = 0;
            for (UUID memberId : List.copyOf(group.memberIds())) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player instanceof EntityPlayerMPFake fakePlayer && isManagedBot(fakePlayer)) {
                    ensureBrain(fakePlayer).mode = mode;
                    affected++;
                }
            }
            broadcast(false);
            return AiResult.success("Set group '" + group.displayName() + "' AI mode to '" + mode.displayName() + "' for " + affected + " bot" + suffix(affected) + ".", affected);
        }

        private List<String> selectedAiSummary() {
            List<String> summary = new ArrayList<>();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                BotBrain brain = ensureBrain(player);
                String groupText = brain.groupKey == null ? "ungrouped" : groups.getOrDefault(brain.groupKey, new BotGroup(brain.groupKey)).displayName();
                summary.add(player.getGameProfile().name() + "=" + brain.mode.displayName() + " (" + groupText + ")");
            }
            return summary;
        }

        private String groupAiSummary(String rawName) {
            BotGroup group = groups.get(normalizeGroupName(rawName));
            if (group == null) {
                return null;
            }
            return "Group '" + group.displayName() + "' has " + group.memberIds().size() + " bot" + suffix(group.memberIds().size()) + " with shared AI mode '" + group.sharedMode().displayName() + "'.";
        }

        private void addSubscriber(UUID subscriber) {
            subscribers.add(subscriber);
        }

        private void removeSubscriber(UUID subscriber) {
            subscribers.remove(subscriber);
        }

        private int queueDepth() {
            return queue.size() + (activeBatch == null ? 0 : 1);
        }

        private BatchProgress progress() {
            return activeBatch == null ? BatchProgress.empty() : activeBatch.progress();
        }

        private void broadcast(boolean openScreen) {
            PlayerBatchSnapshot snapshot = snapshot(server, openScreen);
            for (UUID subscriber : List.copyOf(subscribers)) {
                ServerPlayer player = server.getPlayerList().getPlayer(subscriber);
                if (player == null) {
                    subscribers.remove(subscriber);
                    continue;
                }
                PlayerBatchNetworking.sendState(player, snapshot);
            }
        }

        private void cleanupSelection() {
            selectedPlayers();
        }

        private void cleanupSubscribers() {
            subscribers.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
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
            switch (brain.mode) {
                case IDLE -> {
                }
                case PATROL -> {
                    float nextYaw = fakePlayer.getYRot() + 25.0F;
                    fakePlayer.setYRot(nextYaw);
                    fakePlayer.setYHeadRot(nextYaw);
                }
                case FOLLOW -> {
                    ServerPlayer target = findNearestPlayerTarget(fakePlayer, 24.0D);
                    lookAtTarget(fakePlayer, target);
                    moveTowardTarget(fakePlayer, target, 2.5D, 0.32D);
                }
                case GUARD -> lookAtTarget(fakePlayer, findNearestThreat(fakePlayer, 16.0D));
                case COMBAT -> {
                    LivingEntity target = findNearestThreat(fakePlayer, 10.0D);
                    if (target != null) {
                        lookAtTarget(fakePlayer, target);
                        if (fakePlayer.distanceToSqr(target) <= 9.0D) {
                            fakePlayer.swing(fakePlayer.getUsedItemHand(), true);
                            fakePlayer.attack(target);
                        }
                    }
                }
                case FLEE -> {
                    LivingEntity target = findNearestThreat(fakePlayer, 12.0D);
                    if (target != null) {
                        Vec3 away = fakePlayer.position().subtract(target.position()).normalize().scale(0.35D);
                        fakePlayer.setDeltaMovement(away.x, Math.max(0.1D, fakePlayer.getDeltaMovement().y), away.z);
                    }
                }
            }
        }

        private ServerPlayer findNearestPlayerTarget(EntityPlayerMPFake source, double range) {
            return server.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(source.getUUID()))
                    .filter(player -> !(player instanceof EntityPlayerMPFake))
                    .filter(player -> player.level() == source.level())
                    .filter(player -> player.distanceToSqr(source) <= range * range)
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

        private void moveTowardTarget(EntityPlayerMPFake source, Entity target, double preferredDistance, double speed) {
            if (target == null) {
                return;
            }

            Vec3 offset = target.position().subtract(source.position());
            double distance = offset.length();
            if (distance <= preferredDistance || distance < 0.001D) {
                source.setDeltaMovement(source.getDeltaMovement().multiply(0.35D, 1.0D, 0.35D));
                return;
            }

            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            if (horizontal.lengthSqr() < 0.0001D) {
                return;
            }

            Vec3 motion = horizontal.normalize().scale(speed);
            double verticalBoost = target.getY() > source.getY() + 1.0D ? 0.18D : source.getDeltaMovement().y;
            source.setDeltaMovement(motion.x, Math.max(source.getDeltaMovement().y, verticalBoost), motion.z);
        }

        private void debug(String pattern, Object... args) {
            if (PlayerBatchConfig.isDebugEnabled()) {
                PlayerBatch.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }
    }

    private static final class SummonBatch {
        private final ServerState owner;
        private final MinecraftServer server;
        private final CommandSourceStack feedbackSource;
        private final CommandSourceStack executionSource;
        private final Deque<SummonEntry> remainingEntries;
        private final Set<String> pendingTagNames = new LinkedHashSet<>();
        private final int totalCount;
        private final String formation;
        private final ServerBossEvent bossBar;
        private boolean started;
        private int successCount;
        private int failCount;

        private SummonBatch(ServerState owner, MinecraftServer server, CommandSourceStack source, List<String> names, String formation) {
            this.owner = owner;
            this.server = server;
            this.feedbackSource = source;
            this.executionSource = source.withSuppressedOutput();
            this.formation = formation;
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
            int processedThisTick = Math.max(1, maxSpawnsPerTick);
            for (int index = 0; index < processedThisTick && !remainingEntries.isEmpty(); index++) {
                summonNext();
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
                    if (spawnedPlayer instanceof EntityPlayerMPFake fakePlayer) {
                        owner.markManagedBot(fakePlayer);
                    } else {
                        pendingTagNames.add(nextName);
                    }
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
            return remainingEntries.isEmpty();
        }

        private void finish() {
            bossBar.removeAllPlayers();
            feedbackSource.sendSuccess(
                    () -> Component.literal(
                            "Summoned " + successCount + "/" + totalCount + " bots (" + failCount + " failed)"
                    ).withStyle(failCount > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                    true
            );
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
        private BotAiMode sharedMode = BotAiMode.IDLE;

        private BotGroup(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private Set<UUID> memberIds() {
            return memberIds;
        }

        private BotAiMode sharedMode() {
            return sharedMode;
        }
    }

    private static final class BotBrain {
        private BotAiMode mode = BotAiMode.IDLE;
        private String groupKey;
    }
}

