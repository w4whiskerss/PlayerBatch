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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerBatchService {
    private static final ConcurrentMap<MinecraftServer, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final MobEffectInstance SELECTED_GLOWING = new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false);

    private PlayerBatchService() {
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames) {
        if (source.getServer() == null) {
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

            int queued = state(source.getServer()).queueBatch(source, plannedNames);
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

    public static void requestSummonFromGui(ServerPlayer player, int count, String rawNames) {
        requestSummon(player.createCommandSourceStack(), count, rawNames);
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

    public static boolean toggleSelection(ServerPlayer actor, Entity entity) {
        if (!(entity instanceof EntityPlayerMPFake fakePlayer)) {
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
                selectedNames
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
            List<String> selectedNames
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
        private SummonBatch activeBatch;
        private int syncCooldown;

        private ServerState(MinecraftServer server) {
            this.server = server;
        }

        private int queueBatch(CommandSourceStack source, List<String> names) {
            if (names.isEmpty()) {
                return 0;
            }
            queue.addLast(new SummonBatch(server, source, names));
            debug("Queued summon batch with {} names", names.size());
            broadcast(false);
            return names.size();
        }

        private void tick() {
            cleanupSelection();
            cleanupSubscribers();

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
                if (player instanceof EntityPlayerMPFake fakePlayer) {
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
                    .filter(player -> player instanceof EntityPlayerMPFake)
                    .map(player -> (EntityPlayerMPFake) player)
                    .filter(Objects::nonNull)
                    .toList();
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
            queue.clear();
        }

        private void debug(String pattern, Object... args) {
            if (PlayerBatchConfig.isDebugEnabled()) {
                PlayerBatch.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }
    }

    private static final class SummonBatch {
        private final MinecraftServer server;
        private final CommandSourceStack feedbackSource;
        private final CommandSourceStack executionSource;
        private final Deque<SummonEntry> remainingEntries;
        private final int totalCount;
        private final ServerBossEvent bossBar;
        private boolean started;
        private int successCount;
        private int failCount;

        private SummonBatch(MinecraftServer server, CommandSourceStack source, List<String> names) {
            this.server = server;
            this.feedbackSource = source;
            this.executionSource = source.withSuppressedOutput();
            this.remainingEntries = new ArrayDeque<>(buildCircleEntries(source, names));
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
                if (accepted || server.getPlayerList().getPlayerByName(nextName) != null) {
                    successCount++;
                    debug("Spawn accepted for {}", nextName);
                } else {
                    failCount++;
                    PlayerBatch.LOGGER.warn("Spawn rejected for fake player {}", nextName);
                }
            } catch (Exception exception) {
                failCount++;
                PlayerBatch.LOGGER.error("Failed to execute Carpet summon command for {}", nextName, exception);
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
}

