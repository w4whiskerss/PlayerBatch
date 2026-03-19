package com.zahen.playersummonbulk.core;

import carpet.patches.EntityPlayerMPFake;
import com.zahen.playersummonbulk.PlayerSummonBulk;
import com.zahen.playersummonbulk.compat.CommandCompat;
import com.zahen.playersummonbulk.config.PlayerSummonConfig;
import com.zahen.playersummonbulk.item.SelectionWandItem;
import com.zahen.playersummonbulk.name.NamePlanner;
import com.zahen.playersummonbulk.network.PlayerBatchNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerBatchService {
    private static final ConcurrentMap<MinecraftServer, ServerState> SERVER_STATES = new ConcurrentHashMap<>();

    private PlayerBatchService() {
    }

    public static int requestSummon(CommandSourceStack source, int count, String rawNames) {
        if (source.getServer() == null) {
            return 0;
        }

        int maxCount = PlayerSummonConfig.getMaxSummonCount();
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
                PlayerSummonBulk.LOGGER.error("Failed to plan names for {} bots", count, throwable);
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
        int sanitized = PlayerSummonConfig.setMaxSummonCount(value);
        broadcastState(source.getServer());
        source.sendSuccess(() -> Component.literal("Player batch limit set to " + sanitized + "."), true);
        return 1;
    }

    public static int setSpawnsPerTick(CommandSourceStack source, int value) {
        int sanitized = PlayerSummonConfig.setMaxSpawnsPerTick(value);
        broadcastState(source.getServer());
        source.sendSuccess(() -> Component.literal("Max spawns per tick set to " + sanitized + "."), true);
        return 1;
    }

    public static int setDebug(CommandSourceStack source, boolean enabled) {
        PlayerSummonConfig.setDebugEnabled(enabled);
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

    public static int runSelectedAction(CommandSourceStack source, String action) {
        int affected = state(source.getServer()).runAction(source, action);
        if (affected <= 0) {
            source.sendFailure(Component.literal("No selected fake players matched that action."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Ran action on " + affected + " selected bot" + (affected == 1 ? "" : "s") + "."), true);
        return affected;
    }

    public static int teleportSelection(CommandSourceStack source, String directionName, int blocks) {
        Direction direction = parseDirection(directionName);
        if (direction == null) {
            source.sendFailure(Component.literal("Direction must be one of: north, south, east, west, up, down."));
            return 0;
        }

        int moved = state(source.getServer()).teleportSelection(source, direction, blocks);
        if (moved <= 0) {
            source.sendFailure(Component.literal("No selected fake players were teleported."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Teleported " + moved + " selected bot" + (moved == 1 ? "" : "s") + "."), true);
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

    public static void teleportSelectionFromGui(ServerPlayer player, String direction, int blocks) {
        teleportSelection(player.createCommandSourceStack(), direction, blocks);
    }

    public static void clearSelectionFromGui(ServerPlayer player) {
        clearSelection(player.createCommandSourceStack());
    }

    public static void giveWandFromGui(ServerPlayer player) {
        giveSelectionWand(player.createCommandSourceStack());
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
                PlayerSummonConfig.getMaxSummonCount(),
                PlayerSummonConfig.getMaxSpawnsPerTick(),
                PlayerSummonConfig.isDebugEnabled(),
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
        return Direction.byName(directionName == null ? "" : directionName.toLowerCase(Locale.ROOT));
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
                activeBatch.tick(PlayerSummonConfig.getMaxSpawnsPerTick());
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
                selectedPlayer.setGlowingTag(false);
            }
            int cleared = selectedIds.size();
            selectedIds.clear();
            debug("Cleared {} selections", cleared);
            broadcast(false);
            return cleared;
        }

        private boolean toggleSelection(EntityPlayerMPFake player) {
            UUID id = player.getUUID();
            boolean selected;
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
                player.setGlowingTag(false);
                selected = false;
            } else {
                selectedIds.add(id);
                player.setGlowingTag(true);
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
                    PlayerSummonBulk.LOGGER.warn("Selected action failed: /{}", command);
                }
            }
            broadcast(false);
            return succeeded;
        }

        private int teleportSelection(CommandSourceStack source, Direction direction, int blocks) {
            int moved = 0;
            CommandSourceStack executionSource = source.withSuppressedOutput();
            for (EntityPlayerMPFake player : selectedPlayers()) {
                double x = player.getX() + direction.getStepX() * blocks;
                double y = player.getY() + direction.getStepY() * blocks;
                double z = player.getZ() + direction.getStepZ() * blocks;
                String command = String.format(
                        Locale.ROOT,
                        "tp %s %.3f %.3f %.3f",
                        player.getGameProfile().name(),
                        x,
                        y,
                        z
                );
                boolean success = CommandCompat.performPrefixedCommand(executionSource, command);
                if (success) {
                    moved++;
                    debug("Teleported selected fake player via '/{}'", command);
                }
            }
            broadcast(false);
            return moved;
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
                player.setGlowingTag(false);
            }
            selectedIds.clear();
            subscribers.clear();
            queue.clear();
        }

        private void debug(String pattern, Object... args) {
            if (PlayerSummonConfig.isDebugEnabled()) {
                PlayerSummonBulk.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }
    }

    private static final class SummonBatch {
        private final MinecraftServer server;
        private final CommandSourceStack feedbackSource;
        private final CommandSourceStack executionSource;
        private final Deque<String> remainingNames;
        private final int totalCount;
        private final ServerBossEvent bossBar;
        private boolean started;
        private int successCount;
        private int failCount;

        private SummonBatch(MinecraftServer server, CommandSourceStack source, List<String> names) {
            this.server = server;
            this.feedbackSource = source;
            this.executionSource = source.withSuppressedOutput();
            this.remainingNames = new ArrayDeque<>(names);
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
            for (int index = 0; index < processedThisTick && !remainingNames.isEmpty(); index++) {
                summonNext();
            }
            updateBossBar();
        }

        private void summonNext() {
            String nextName = remainingNames.removeFirst();
            String command = "player " + nextName + " spawn";
            try {
                boolean accepted = CommandCompat.performPrefixedCommand(executionSource, command);
                if (accepted || server.getPlayerList().getPlayerByName(nextName) != null) {
                    successCount++;
                    debug("Spawn accepted for {}", nextName);
                } else {
                    failCount++;
                    PlayerSummonBulk.LOGGER.warn("Spawn rejected for fake player {}", nextName);
                }
            } catch (Exception exception) {
                failCount++;
                PlayerSummonBulk.LOGGER.error("Failed to execute Carpet summon command for {}", nextName, exception);
            }
        }

        private BatchProgress progress() {
            return new BatchProgress(true, totalCount, successCount, failCount, remainingNames.size());
        }

        private boolean isComplete() {
            return remainingNames.isEmpty();
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
            if (PlayerSummonConfig.isDebugEnabled()) {
                PlayerSummonBulk.LOGGER.info("[PlayerBatch] " + pattern, args);
            }
        }
    }
}
