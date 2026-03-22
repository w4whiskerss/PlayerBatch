package com.w4whiskers.playerbatch.command;

import com.w4whiskers.playerbatch.core.BotLoadout;
import com.w4whiskers.playerbatch.core.CombatPresetSpec;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CombatPresetParser {
    public static final List<String> OPTION_SUGGESTIONS = List.of(
            "-combat{true}",
            "-combat{false}",
            "-ironarmor",
            "-goldenarmor",
            "-diamondarmor",
            "-netheritearmor",
            "-irontools",
            "-diamondtools",
            "-netheritetools",
            "-shield",
            "-totem{5}",
            "-reach{3}",
            "-stap{true}",
            "-stap{false}",
            "-damage{true}",
            "-damage{false}",
            "-360flex{true}",
            "-360flex{false}",
            "-healingitems{false}",
            "-healingitems{true, golden_apple*32, splash_potion_of_healing*9}"
    );

    private CombatPresetParser() {
    }

    public static CombatPresetSpec parse(String rawOptions) {
        CombatPresetSpec.ArmorTier armorTier = CombatPresetSpec.ArmorTier.NONE;
        CombatPresetSpec.ToolTier toolTier = CombatPresetSpec.ToolTier.NONE;
        CombatPresetSpec.OffhandMode offhandMode = CombatPresetSpec.OffhandMode.SHIELD;
        int offhandCount = 1;
        boolean selfHeal = false;
        boolean healingItemsEnabled = false;
        List<BotLoadout.StackSpec> healingItems = new ArrayList<>();
        int reach = 3;
        boolean stapEnabled = false;
        boolean damageEnabled = true;
        boolean flex360Enabled = false;

        for (String token : splitOptions(rawOptions)) {
            String normalized = normalizeOption(token);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.startsWith("combat")) {
                parseBraceBoolean(normalized, true);
                continue;
            }
            CombatPresetSpec.ArmorTier parsedArmor = CombatPresetSpec.ArmorTier.fromOptionToken(normalized);
            if (parsedArmor != CombatPresetSpec.ArmorTier.NONE) {
                armorTier = parsedArmor;
                continue;
            }
            CombatPresetSpec.ToolTier parsedTool = CombatPresetSpec.ToolTier.fromOptionToken(normalized);
            if (parsedTool != CombatPresetSpec.ToolTier.NONE) {
                toolTier = parsedTool;
                continue;
            }
            if (normalized.equals("shield")) {
                offhandMode = CombatPresetSpec.OffhandMode.SHIELD;
                offhandCount = 1;
                continue;
            }
            if (normalized.startsWith("totem")) {
                offhandMode = CombatPresetSpec.OffhandMode.TOTEM;
                offhandCount = Math.max(1, parseBraceInt(normalized, 1));
                continue;
            }
            if (normalized.startsWith("reach")) {
                reach = parseRangedBraceInt(normalized, 3, 1, 10);
                continue;
            }
            if (normalized.startsWith("stap")) {
                stapEnabled = parseBraceBoolean(normalized, false);
                continue;
            }
            if (normalized.startsWith("damage")) {
                damageEnabled = parseBraceBoolean(normalized, true);
                continue;
            }
            if (normalized.startsWith("360flex")) {
                flex360Enabled = parseBraceBoolean(normalized, false);
                continue;
            }
            if (normalized.startsWith("healingitems")) {
                HealingItemsParseResult result = parseHealingItems(normalized);
                healingItemsEnabled = result.enabled();
                healingItems = result.items();
                selfHeal = result.enabled() || !result.items().isEmpty();
            }
        }

        return new CombatPresetSpec(
                armorTier,
                toolTier,
                offhandMode,
                offhandCount,
                selfHeal,
                healingItemsEnabled,
                healingItems,
                reach,
                true,
                stapEnabled,
                damageEnabled,
                flex360Enabled
        );
    }

    public static boolean shouldCreatePreset(String rawOptions) {
        for (String token : splitOptions(rawOptions)) {
            String normalized = normalizeOption(token);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.startsWith("combat")) {
                if (parseBraceBoolean(normalized, true)) {
                    return true;
                }
                continue;
            }
            if (optionKey(normalized) != null) {
                return true;
            }
        }
        return false;
    }

    public static String validate(String rawOptions) {
        for (String token : splitOptions(rawOptions)) {
            String normalized = normalizeOption(token);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.startsWith("combat")) {
                parseBraceBoolean(normalized, true);
                continue;
            }
            if (CombatPresetSpec.ArmorTier.fromOptionToken(normalized) != CombatPresetSpec.ArmorTier.NONE) {
                continue;
            }
            if (CombatPresetSpec.ToolTier.fromOptionToken(normalized) != CombatPresetSpec.ToolTier.NONE) {
                continue;
            }
            if (normalized.equals("shield")) {
                continue;
            }
            if (normalized.startsWith("totem")) {
                parseBraceInt(normalized, 1);
                continue;
            }
            if (normalized.startsWith("reach")) {
                parseRangedBraceInt(normalized, 3, 1, 10);
                continue;
            }
            if (normalized.startsWith("stap")) {
                parseBraceBoolean(normalized, false);
                continue;
            }
            if (normalized.startsWith("damage")) {
                parseBraceBoolean(normalized, true);
                continue;
            }
            if (normalized.startsWith("360flex")) {
                parseBraceBoolean(normalized, false);
                continue;
            }
            if (normalized.startsWith("healingitems")) {
                parseHealingItems(normalized);
                continue;
            }
            throw new IllegalArgumentException("Unknown combat preset option: -" + normalized);
        }
        return rawOptions == null ? "" : rawOptions.trim();
    }

    public static List<String> suggestOptions(String currentInput) {
        String lower = currentInput == null ? "" : currentInput.toLowerCase(Locale.ROOT);
        List<String> tokens = splitOptions(lower);
        String active = tokens.isEmpty() ? lower.stripLeading() : tokens.get(tokens.size() - 1);
        Set<String> usedKeys = new LinkedHashSet<>();
        for (int index = 0; index < Math.max(0, tokens.size() - 1); index++) {
            String normalized = normalizeOption(tokens.get(index));
            String key = optionKey(normalized);
            if (key != null) {
                usedKeys.add(key);
            }
        }
        String activeKey = optionKey(normalizeOption(active));
        List<String> suggestions = new ArrayList<>();
        for (String option : OPTION_SUGGESTIONS) {
            String key = optionKey(normalizeOption(option));
            if (key != null && usedKeys.contains(key) && !key.equals(activeKey)) {
                continue;
            }
            if (active.isBlank() || option.startsWith(active)) {
                suggestions.add(option);
            }
        }
        return suggestions;
    }

    public static boolean isKnownOptionToken(String token) {
        String normalized = normalizeOption(token);
        if (normalized.isBlank()) {
            return false;
        }
        try {
            validate("-" + normalized);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String optionKey(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (CombatPresetSpec.ArmorTier.fromOptionToken(normalized) != CombatPresetSpec.ArmorTier.NONE) {
            return "armor";
        }
        if (normalized.startsWith("combat")) {
            return "combat";
        }
        if (CombatPresetSpec.ToolTier.fromOptionToken(normalized) != CombatPresetSpec.ToolTier.NONE) {
            return "tools";
        }
        if (normalized.equals("shield") || normalized.startsWith("totem")) {
            return "offhand";
        }
        if (normalized.startsWith("reach")) {
            return "reach";
        }
        if (normalized.startsWith("stap")) {
            return "stap";
        }
        if (normalized.startsWith("damage")) {
            return "damage";
        }
        if (normalized.startsWith("360flex")) {
            return "360flex";
        }
        if (normalized.startsWith("healingitems")) {
            return "healingitems";
        }
        return null;
    }

    public static List<String> healingItemSuggestions() {
        Set<String> suggestions = new LinkedHashSet<>();
        suggestions.add("golden_apple");
        suggestions.add("enchanted_golden_apple");
        suggestions.add("potion_of_healing");
        suggestions.add("splash_potion_of_healing");
        suggestions.add("lingering_potion_of_healing");
        suggestions.add("potion_of_regeneration");
        suggestions.add("splash_potion_of_regeneration");
        suggestions.add("lingering_potion_of_regeneration");
        suggestions.addAll(BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).filter(id ->
                id.contains("apple") || id.contains("stew") || id.contains("bread") || id.contains("carrot") || id.contains("potato")).toList());
        return List.copyOf(suggestions);
    }

    private static List<String> splitOptions(String rawOptions) {
        List<String> tokens = new ArrayList<>();
        if (rawOptions == null || rawOptions.isBlank()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        for (char character : rawOptions.toCharArray()) {
            if (character == '{') {
                braceDepth++;
            } else if (character == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            }
            if (Character.isWhitespace(character) && braceDepth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String normalizeOption(String token) {
        String trimmed = token == null ? "" : token.trim();
        while (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static int parseBraceInt(String token, int fallback) {
        String inside = insideBraces(token);
        if (inside.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(inside.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number in option: -" + token);
        }
    }

    private static int parseRangedBraceInt(String token, int fallback, int min, int max) {
        int parsed = parseBraceInt(token, fallback);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("Expected a number between " + min + " and " + max + " in option: -" + token);
        }
        return parsed;
    }

    private static boolean parseBraceBoolean(String token, boolean fallback) {
        String inside = insideBraces(token);
        if (inside.isBlank()) {
            return fallback;
        }
        String normalized = inside.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true")) {
            return true;
        }
        if (normalized.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("Expected true or false in option: -" + token);
    }

    private static HealingItemsParseResult parseHealingItems(String token) {
        String inside = insideBraces(token);
        if (inside.isBlank()) {
            return new HealingItemsParseResult(false, List.of());
        }
        String[] pieces = inside.split(",");
        if (pieces.length == 0) {
            return new HealingItemsParseResult(false, List.of());
        }
        String flag = pieces[0].trim().toLowerCase(Locale.ROOT);
        if (flag.equals("false")) {
            return new HealingItemsParseResult(false, List.of());
        }
        if (!flag.equals("true")) {
            throw new IllegalArgumentException("healingitems must start with true or false.");
        }
        List<BotLoadout.StackSpec> items = new ArrayList<>();
        for (int index = 1; index < pieces.length; index++) {
            String rawItem = pieces[index].trim();
            if (rawItem.isEmpty()) {
                continue;
            }
            items.add(parseItemSpec(rawItem));
        }
        return new HealingItemsParseResult(true, items);
    }

    private static BotLoadout.StackSpec parseItemSpec(String rawItem) {
        String itemId = rawItem;
        int count = 1;
        if (rawItem.contains("*")) {
            String[] split = rawItem.split("\\*", 2);
            itemId = split[0].trim();
            try {
                count = Integer.parseInt(split[1].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid item count: " + rawItem);
            }
        }
        String normalized = normalizeItemId(itemId);
        if (!isKnownHealingAlias(normalized) && BuiltInRegistries.ITEM.keySet().stream().noneMatch(key -> key.toString().equals(normalized))) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        return new BotLoadout.StackSpec(normalized, Math.max(1, count));
    }

    private static boolean isKnownHealingAlias(String normalized) {
        return normalized.equals("minecraft:potion_of_healing")
                || normalized.equals("minecraft:splash_potion_of_healing")
                || normalized.equals("minecraft:lingering_potion_of_healing")
                || normalized.equals("minecraft:potion_of_regeneration")
                || normalized.equals("minecraft:splash_potion_of_regeneration")
                || normalized.equals("minecraft:lingering_potion_of_regeneration");
    }

    private static String normalizeItemId(String rawItem) {
        String trimmed = rawItem.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private static String insideBraces(String token) {
        int start = token.indexOf('{');
        int end = token.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return token.substring(start + 1, end).trim();
    }

    private record HealingItemsParseResult(boolean enabled, List<BotLoadout.StackSpec> items) {
    }
}
