package com.zahen.playerbatch.name;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class RandomNameGenerator {
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
        Set<String> excludedLower = new LinkedHashSet<>();
        for (String excludedName : excludedNames) {
            excludedLower.add(excludedName.toLowerCase(Locale.ROOT));
        }
        while (names.size() < count) {
            String candidate = generateName();
            String lowered = candidate.toLowerCase(Locale.ROOT);
            if (!excludedLower.contains(lowered)) {
                names.add(candidate);
                excludedLower.add(lowered);
            }
        }
        return names;
    }

    private static String generateName() {
        String candidate = PREFIXES.get(RANDOM.nextInt(PREFIXES.size()))
                + SUFFIXES.get(RANDOM.nextInt(SUFFIXES.size()));
        if (RANDOM.nextInt(5) == 0) {
            candidate += RANDOM.nextInt(90) + 10;
        }
        return candidate.length() > MAX_LENGTH ? candidate.substring(0, MAX_LENGTH) : candidate;
    }
}

