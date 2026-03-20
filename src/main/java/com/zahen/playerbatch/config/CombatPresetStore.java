package com.zahen.playerbatch.config;

import com.zahen.playerbatch.PlayerBatch;
import com.zahen.playerbatch.core.CombatPresetSpec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class CombatPresetStore {
    private static final String FILE_NAME = "playerbatch-combat-presets.properties";

    private CombatPresetStore() {
    }

    public static void save(String name, int count, String rawOptions) {
        Properties properties = loadProperties();
        String prefix = "preset." + normalizeName(name) + ".";
        properties.setProperty(prefix + "name", name);
        properties.setProperty(prefix + "count", Integer.toString(count));
        properties.setProperty(prefix + "options", rawOptions == null ? "" : rawOptions.trim());
        storeProperties(properties);
    }

    public static CombatPresetSpec.SavedCombatPreset get(String name) {
        Properties properties = loadProperties();
        String prefix = "preset." + normalizeName(name) + ".";
        String displayName = properties.getProperty(prefix + "name");
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        int count = parseInt(properties.getProperty(prefix + "count"), 1);
        String options = properties.getProperty(prefix + "options", "");
        return new CombatPresetSpec.SavedCombatPreset(displayName, count, options);
    }

    public static List<String> names() {
        Properties properties = loadProperties();
        List<String> names = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("preset.") && key.endsWith(".name")) {
                names.add(properties.getProperty(key));
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private static Properties loadProperties() {
        Path path = path();
        Properties properties = new Properties();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                return properties;
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            PlayerBatch.LOGGER.error("Failed to load combat presets", exception);
        }
        return properties;
    }

    private static void storeProperties(Properties properties) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "PlayerBatch combat presets");
            }
        } catch (IOException exception) {
            PlayerBatch.LOGGER.error("Failed to save combat presets", exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
