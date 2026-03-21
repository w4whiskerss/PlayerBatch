package com.w4whiskers.playerbatch.config;

import com.w4whiskers.playerbatch.PlayerBatch;
import com.w4whiskers.playerbatch.core.BotLoadout;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class KitStore {
    private static final String FILE_NAME = "playerbatch-kits.properties";

    private KitStore() {
    }

    public static void save(String name, BotLoadout loadout) {
        Properties properties = loadProperties();
        String prefix = "kit." + normalizeName(name) + ".";
        properties.setProperty(prefix + "name", name);
        loadout.writeTo(properties, prefix);
        storeProperties(properties);
    }

    public static BotLoadout get(String name) {
        Properties properties = loadProperties();
        String prefix = "kit." + normalizeName(name) + ".";
        String displayName = properties.getProperty(prefix + "name");
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return BotLoadout.readFrom(properties, prefix);
    }

    public static List<String> names() {
        Properties properties = loadProperties();
        List<String> names = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("kit.") && key.endsWith(".name")) {
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
            PlayerBatch.LOGGER.error("Failed to load saved kits", exception);
        }
        return properties;
    }

    private static void storeProperties(Properties properties) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "PlayerBatch saved kits");
            }
        } catch (IOException exception) {
            PlayerBatch.LOGGER.error("Failed to save kit data", exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
