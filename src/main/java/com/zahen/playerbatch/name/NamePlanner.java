package com.zahen.playerbatch.name;

import com.zahen.playerbatch.PlayerBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class NamePlanner {
    private static final List<String> FALLBACK_POOL = List.of(
            "RedstoneFox",
            "PixelCrafter",
            "StoneHarbor",
            "MapleKnight",
            "CopperBloom",
            "LunarForge",
            "RiverRune",
            "MossyTrail",
            "CloudHarbor",
            "AshenVale",
            "CinderPeak",
            "BirchSignal",
            "PrismRunner",
            "NovaHarbor",
            "VelvetPine",
            "FrostPebble",
            "AmberTrail",
            "EchoLantern",
            "HazelQuest",
            "GoldenSpruce"
    );

    private NamePlanner() {
    }

    public static CompletableFuture<List<String>> planNamesAsync(int count, List<String> preferredNames) {
        Set<String> planned = new LinkedHashSet<>();
        for (String preferredName : preferredNames) {
            if (NameValidation.isAllowedRequestedName(preferredName)) {
                planned.add(preferredName);
            }
        }

        if (planned.size() >= count) {
            return CompletableFuture.completedFuture(new ArrayList<>(planned).subList(0, count));
        }

        int fetchTarget = Math.max(count * 3, count + 20);
        return NameFetcher.fetchNamesAsync(fetchTarget).handle((fetchedNames, throwable) -> {
            if (throwable != null) {
                PlayerBatch.LOGGER.error("Name planning fetch failed", throwable);
            } else if (fetchedNames != null) {
                for (String fetchedName : fetchedNames) {
                    if (planned.size() >= count) {
                        break;
                    }
                    if (NameValidation.isAllowedRequestedName(fetchedName)) {
                        planned.add(fetchedName);
                    }
                }
            }

            for (String fallbackName : FALLBACK_POOL) {
                if (planned.size() >= count) {
                    break;
                }
                planned.add(fallbackName);
            }

            if (planned.size() < count) {
                planned.addAll(RandomNameGenerator.generateUniqueNames(count - planned.size(), planned));
            }

            List<String> finalNames = new ArrayList<>(planned);
            Collections.shuffle(finalNames);
            return new ArrayList<>(finalNames.subList(0, Math.min(count, finalNames.size())));
        });
    }

    public static List<String> parseRequestedNames(String rawNames) {
        if (rawNames == null || rawNames.isBlank()) {
            return List.of();
        }

        String normalized = rawNames.trim();
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String piece : normalized.split(",")) {
            String candidate = piece.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!NameValidation.isAllowedRequestedName(candidate)) {
                throw new IllegalArgumentException("Invalid requested name: " + candidate);
            }
            if (!unique.add(candidate)) {
                throw new IllegalArgumentException("Duplicate requested name: " + candidate);
            }
            names.add(candidate);
        }
        return names;
    }
}

