package com.w4whiskers.playerbatch.ext;

import com.w4whiskers.playerbatch.PlayerBatch;
import com.w4whiskers.playerbatch.compat.CommandCompat;
import com.w4whiskers.playerbatch.config.PlayerBatchConfig;
import com.w4whiskers.playerbatch.extapi.PlayerBatchAction;
import com.w4whiskers.playerbatch.extapi.PlayerBatchArgument;
import com.w4whiskers.playerbatch.extapi.PlayerBatchBotController;
import com.w4whiskers.playerbatch.extapi.PlayerBatchBehavior;
import com.w4whiskers.playerbatch.extapi.PlayerBatchContext;
import com.w4whiskers.playerbatch.extapi.PlayerBatchExtensionEntrypoint;
import com.w4whiskers.playerbatch.extapi.PlayerBatchFormation;
import com.w4whiskers.playerbatch.extapi.PlayerBatchRegistrar;
import com.w4whiskers.playerbatch.extapi.PlayerBatchSummonPlan;
import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerBatchExtensionManager {
    private static final Set<String> BUILTIN_FORMATIONS = Set.of("circle", "filled circle", "filled_circle", "dense", "square", "triangle", "random", "single block", "single_block");
    private static final PlayerBatchExtensionManager INSTANCE = new PlayerBatchExtensionManager();

    private final Map<String, PlayerBatchFormation> formations = new LinkedHashMap<>();
    private final Map<String, PlayerBatchArgument> arguments = new LinkedHashMap<>();
    private final Map<String, PlayerBatchAction> actions = new LinkedHashMap<>();
    private final Map<String, PlayerBatchBehavior> behaviors = new LinkedHashMap<>();
    private boolean initialized;

    private PlayerBatchExtensionManager() {
    }

    public static void initialize() {
        INSTANCE.initOnce();
    }

    public static Collection<String> formationIds() {
        List<String> ids = new ArrayList<>(BUILTIN_FORMATIONS);
        ids.addAll(INSTANCE.formations.keySet());
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    public static boolean isKnownFormation(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return BUILTIN_FORMATIONS.contains(normalized) || INSTANCE.formations.containsKey(normalized);
    }

    public static PlayerBatchFormation formation(String id) {
        if (id == null) {
            return null;
        }
        return INSTANCE.formations.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static Collection<PlayerBatchArgument> arguments() {
        return List.copyOf(INSTANCE.arguments.values());
    }

    public static Collection<PlayerBatchAction> actions() {
        return List.copyOf(INSTANCE.actions.values());
    }

    public static Collection<PlayerBatchBehavior> behaviors() {
        return List.copyOf(INSTANCE.behaviors.values());
    }

    public static PlayerBatchContext contextSnapshot() {
        return new PlayerBatchContext(
                PlayerBatch.MOD_ID,
                PlayerBatchConfig.getMaxSummonCount(),
                PlayerBatchConfig.getMaxSpawnsPerTick(),
                PlayerBatchConfig.isDebugEnabled(),
                Map.of()
        );
    }

    public static boolean applyArgumentToken(String token, PlayerBatchSummonPlan plan) {
        for (PlayerBatchArgument argument : INSTANCE.arguments.values()) {
            try {
                if (argument.handler().apply(token, plan, contextSnapshot())) {
                    return true;
                }
            } catch (Exception exception) {
                PlayerBatch.LOGGER.error("PlayerBatch extension argument failed for token {}", token, exception);
            }
        }
        return false;
    }

    public static int executeAction(String rawAction, Collection<EntityPlayerMPFake> bots, MinecraftServer server, CommandSourceStack source) {
        String actionKey = rawAction == null ? "" : rawAction.trim().toLowerCase(Locale.ROOT);
        if (actionKey.isEmpty()) {
            return -1;
        }
        PlayerBatchAction matched = null;
        for (PlayerBatchAction action : INSTANCE.actions.values()) {
            if (matchesAction(action, actionKey)) {
                matched = action;
                break;
            }
        }
        if (matched == null) {
            return -1;
        }
        List<PlayerBatchBotController> controllers = bots.stream()
                .map(bot -> new BotController(bot, source))
                .map(controller -> (PlayerBatchBotController) controller)
                .toList();
        try {
            return matched.handler().execute(rawAction, controllers, contextSnapshot());
        } catch (Exception exception) {
            PlayerBatch.LOGGER.error("PlayerBatch extension action failed for {}", rawAction, exception);
            return 0;
        }
    }

    public static void applyBehaviors(EntityPlayerMPFake bot, PlayerBatchSummonPlan plan, CommandSourceStack source) {
        if (plan == null || plan.behaviorIds().isEmpty()) {
            return;
        }
        BotController controller = new BotController(bot, source);
        for (String behaviorId : plan.behaviorIds()) {
            PlayerBatchBehavior behavior = INSTANCE.behaviors.get(behaviorId.toLowerCase(Locale.ROOT));
            if (behavior == null) {
                continue;
            }
            try {
                behavior.handler().apply(controller, plan.copy(), contextSnapshot());
            } catch (Exception exception) {
                PlayerBatch.LOGGER.error("PlayerBatch extension behavior failed for {}", behaviorId, exception);
            }
        }
    }

    private void initOnce() {
        if (initialized) {
            return;
        }
        initialized = true;
        Registrar registrar = new Registrar();
        List<PlayerBatchExtensionEntrypoint> entrypoints = FabricLoader.getInstance()
                .getEntrypoints("playerbatch-ext", PlayerBatchExtensionEntrypoint.class);
        for (PlayerBatchExtensionEntrypoint entrypoint : entrypoints) {
            try {
                entrypoint.register(registrar);
            } catch (Exception exception) {
                PlayerBatch.LOGGER.error("Failed to register PlayerBatch extension {}", entrypoint.getClass().getName(), exception);
            }
        }
        PlayerBatch.LOGGER.info("Loaded {} PlayerBatch extensions", entrypoints.size());
    }

    private static boolean matchesAction(PlayerBatchAction action, String rawAction) {
        if (action.id().equalsIgnoreCase(rawAction)) {
            return true;
        }
        for (String alias : action.aliases()) {
            if (alias.equalsIgnoreCase(rawAction)) {
                return true;
            }
        }
        return false;
    }

    private final class Registrar implements PlayerBatchRegistrar {
        @Override
        public void registerFormation(PlayerBatchFormation formation) {
            String key = normalizeKey(formation.id());
            formations.put(key, formation);
        }

        @Override
        public void registerArgument(PlayerBatchArgument argument) {
            String key = normalizeKey(argument.key());
            arguments.put(key, argument);
        }

        @Override
        public void registerAction(PlayerBatchAction action) {
            String key = normalizeKey(action.id());
            actions.put(key, action);
        }

        @Override
        public void registerBehavior(PlayerBatchBehavior behavior) {
            String key = normalizeKey(behavior.id());
            behaviors.put(key, behavior);
        }

        @Override
        public Collection<PlayerBatchFormation> formations() {
            return PlayerBatchExtensionManager.formationIds().stream()
                    .map(PlayerBatchExtensionManager::formation)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public Collection<PlayerBatchArgument> arguments() {
            return PlayerBatchExtensionManager.arguments();
        }

        @Override
        public Collection<PlayerBatchAction> actions() {
            return PlayerBatchExtensionManager.actions();
        }

        @Override
        public Collection<PlayerBatchBehavior> behaviors() {
            return PlayerBatchExtensionManager.behaviors();
        }

        private String normalizeKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Extension id/key cannot be empty.");
            }
            return key.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final class BotController implements PlayerBatchBotController {
        private final EntityPlayerMPFake bot;
        private final CommandSourceStack source;

        private BotController(EntityPlayerMPFake bot, CommandSourceStack source) {
            this.bot = bot;
            this.source = source.withSuppressedOutput();
        }

        @Override
        public java.util.UUID uuid() {
            return bot.getUUID();
        }

        @Override
        public String name() {
            return bot.getGameProfile().name();
        }

        @Override
        public void addTag(String tag) {
            bot.addTag(tag);
        }

        @Override
        public void removeTag(String tag) {
            bot.removeTag(tag);
        }

        @Override
        public boolean runAction(String action) {
            return CommandCompat.performPrefixedCommand(source, "player " + bot.getGameProfile().name() + " " + action.trim());
        }
    }
}
