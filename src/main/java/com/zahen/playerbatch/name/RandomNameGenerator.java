package com.zahen.playerbatch.name;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class RandomNameGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 16;
    private static final Random RANDOM = new Random();
    private static final List<String> PREFIXES = List.of(
            "Stone", "River", "Maple", "Solar", "Frost", "Amber", "Pixel", "Copper", "Nova", "Birch"
    );
    private static final List<String> SUFFIXES = List.of(
            "Fox", "Bloom", "Forge", "Trail", "Rune", "Vale", "Quest", "Spark", "Harbor", "Knight"
    );

    private RandomNameGenerator() {
    }

    public static Set<String> generateUniqueNames(int count, Set<String> excludedNames) {
        Set<String> names = new LinkedHashSet<>();
        while (names.size() < count) {
            String candidate = generateName();
            if (!excludedNames.contains(candidate)) {
                names.add(candidate);
            }
        }
        return names;
    }

    private static String generateName() {
        if (RANDOM.nextBoolean()) {
            String candidate = PREFIXES.get(RANDOM.nextInt(PREFIXES.size()))
                    + SUFFIXES.get(RANDOM.nextInt(SUFFIXES.size()));
            if (RANDOM.nextBoolean()) {
                candidate += RANDOM.nextInt(90) + 10;
            }
            return candidate.length() > MAX_LENGTH ? candidate.substring(0, MAX_LENGTH) : candidate;
        }

        int length = RANDOM.nextInt(MAX_LENGTH - MIN_LENGTH + 1) + MIN_LENGTH;
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return builder.toString();
    }
}

