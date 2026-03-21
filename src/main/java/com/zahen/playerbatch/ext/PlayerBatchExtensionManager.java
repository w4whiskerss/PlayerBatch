package com.zahen.playerbatch.ext;

import com.zahen.playerbatch.PlayerBatch;
import com.zahen.playerbatch.config.PlayerBatchConfig;
import com.zahen.playerbatch.extapi.PlayerBatchAction;
import com.zahen.playerbatch.extapi.PlayerBatchArgument;
import com.zahen.playerbatch.extapi.PlayerBatchBehavior;
import com.zahen.playerbatch.extapi.PlayerBatchContext;
import com.zahen.playerbatch.extapi.PlayerBatchExtensionEntrypoint;
import com.zahen.playerbatch.extapi.PlayerBatchFormation;
import com.zahen.playerbatch.extapi.PlayerBatchRegistrar;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerBatchExtensionManager {
    private static final Set<String> BUILTIN_FORMATIONS = Set.of("circle", "square", "triangle", "random", "single block", "single_block");
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
}
