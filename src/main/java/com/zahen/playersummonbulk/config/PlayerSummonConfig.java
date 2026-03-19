package com.zahen.playersummonbulk.config;

import com.zahen.playersummonbulk.PlayerSummonBulk;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PlayerSummonConfig {
    private static final String MAX_SUMMON_COUNT_KEY = "maxSummonCount";
    private static final String MAX_SPAWNS_PER_TICK_KEY = "maxSpawnsPerTick";
    private static final String DEBUG_ENABLED_KEY = "debugEnabled";
    private static final int DEFAULT_MAX_SUMMON_COUNT = 256;
    private static final int DEFAULT_MAX_SPAWNS_PER_TICK = 4;

    private static int maxSummonCount = DEFAULT_MAX_SUMMON_COUNT;
    private static int maxSpawnsPerTick = DEFAULT_MAX_SPAWNS_PER_TICK;
    private static boolean debugEnabled = false;
    private static Path configPath;

    private PlayerSummonConfig() {
    }

    public static void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("playersummonbulk.properties");
        Properties properties = new Properties();

        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                properties.setProperty(MAX_SUMMON_COUNT_KEY, Integer.toString(DEFAULT_MAX_SUMMON_COUNT));
                properties.setProperty(MAX_SPAWNS_PER_TICK_KEY, Integer.toString(DEFAULT_MAX_SPAWNS_PER_TICK));
                properties.setProperty(DEBUG_ENABLED_KEY, Boolean.toString(false));
                try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                    properties.store(outputStream, "PlayerSummonBulk configuration");
                }
            }

            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            }
            maxSummonCount = sanitizeMax(properties.getProperty(MAX_SUMMON_COUNT_KEY));
            maxSpawnsPerTick = sanitizeSpawnsPerTick(properties.getProperty(MAX_SPAWNS_PER_TICK_KEY));
            debugEnabled = Boolean.parseBoolean(properties.getProperty(DEBUG_ENABLED_KEY, "false"));
            PlayerSummonBulk.LOGGER.info(
                    "Loaded PlayerSummonBulk config with maxSummonCount={}, maxSpawnsPerTick={}, debugEnabled={}",
                    maxSummonCount,
                    maxSpawnsPerTick,
                    debugEnabled
            );
        } catch (IOException exception) {
            maxSummonCount = DEFAULT_MAX_SUMMON_COUNT;
            maxSpawnsPerTick = DEFAULT_MAX_SPAWNS_PER_TICK;
            debugEnabled = false;
            PlayerSummonBulk.LOGGER.error(
                    "Failed to load config, using defaults maxSummonCount={}, maxSpawnsPerTick={}, debugEnabled={}",
                    maxSummonCount,
                    maxSpawnsPerTick,
                    debugEnabled,
                    exception
            );
        }
    }

    public static int getMaxSummonCount() {
        return maxSummonCount;
    }

    public static int getMaxSpawnsPerTick() {
        return maxSpawnsPerTick;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static int setMaxSummonCount(int newMaxSummonCount) {
        maxSummonCount = Math.max(1, newMaxSummonCount);
        save();
        return maxSummonCount;
    }

    public static int setMaxSpawnsPerTick(int newMaxSpawnsPerTick) {
        maxSpawnsPerTick = Math.max(1, newMaxSpawnsPerTick);
        save();
        return maxSpawnsPerTick;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        save();
    }

    private static int sanitizeMax(String rawValue) {
        if (rawValue == null) {
            return DEFAULT_MAX_SUMMON_COUNT;
        }

        try {
            return Math.max(1, Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException exception) {
            PlayerSummonBulk.LOGGER.warn(
                    "Invalid {} value '{}', using default {}",
                    MAX_SUMMON_COUNT_KEY,
                    rawValue,
                    DEFAULT_MAX_SUMMON_COUNT
            );
            return DEFAULT_MAX_SUMMON_COUNT;
        }
    }

    private static int sanitizeSpawnsPerTick(String rawValue) {
        if (rawValue == null) {
            return DEFAULT_MAX_SPAWNS_PER_TICK;
        }

        try {
            return Math.max(1, Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException exception) {
            PlayerSummonBulk.LOGGER.warn(
                    "Invalid {} value '{}', using default {}",
                    MAX_SPAWNS_PER_TICK_KEY,
                    rawValue,
                    DEFAULT_MAX_SPAWNS_PER_TICK
            );
            return DEFAULT_MAX_SPAWNS_PER_TICK;
        }
    }

    private static void save() {
        if (configPath == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(MAX_SUMMON_COUNT_KEY, Integer.toString(maxSummonCount));
        properties.setProperty(MAX_SPAWNS_PER_TICK_KEY, Integer.toString(maxSpawnsPerTick));
        properties.setProperty(DEBUG_ENABLED_KEY, Boolean.toString(debugEnabled));

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                properties.store(outputStream, "PlayerSummonBulk configuration");
            }
        } catch (IOException exception) {
            PlayerSummonBulk.LOGGER.error("Failed to save PlayerSummonBulk config", exception);
        }
    }
}
