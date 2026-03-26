package com.w4whiskers.playerbatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.w4whiskers.playerbatch.config.CombatPresetStore;
import com.w4whiskers.playerbatch.config.KitStore;
import com.w4whiskers.playerbatch.core.BotAiMode;
import com.w4whiskers.playerbatch.core.PlayerBatchService;
import com.w4whiskers.playerbatch.ext.PlayerBatchExtensionManager;
import com.w4whiskers.playerbatch.extapi.PlayerBatchAction;
import com.w4whiskers.playerbatch.extapi.PlayerBatchArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerBatchCommand {
    private static final List<String> DIRECTION_SUGGESTIONS = List.of("up", "below", "north", "south", "east", "west");
    private static final List<String> SLOT_SUGGESTIONS = List.of("head", "chest", "legs", "feet", "mainhand", "offhand");
    private static final List<String> FORMATION_SUGGESTIONS = List.of("circle", "filled_circle", "dense", "square", "triangle", "random", "single_block");
    private static final List<String> AI_MODE_SUGGESTIONS = List.of(
            BotAiMode.IDLE.displayName(),
            BotAiMode.COMBAT.displayName(),
            BotAiMode.PATROL.displayName(),
            BotAiMode.GUARD.displayName(),
            BotAiMode.FOLLOW.displayName(),
            BotAiMode.FLEE.displayName()
    );
    private static final List<String> ACTION_SUGGESTIONS = List.of(
            "stop",
            "use",
            "use once",
            "use continuous",
            "use interval 20",
            "jump",
            "jump once",
            "jump continuous",
            "jump interval 20",
            "attack",
            "attack once",
            "attack continuous",
            "attack interval 20",
            "drop all",
            "drop mainhand",
            "drop offhand",
            "drop 0",
            "dropStack all",
            "dropStack mainhand",
            "dropStack offhand",
            "dropStack 0",
            "swapHands",
            "hotbar 1",
            "kill",
            "mount",
            "mount anything",
            "dismount",
            "sneak",
            "unsneak",
            "sprint",
            "unsprint",
            "look north",
            "look south",
            "look east",
            "look west",
            "look up",
            "look down",
            "turn left",
            "turn right",
            "turn back",
            "move",
            "move forward",
            "move backward",
            "move left",
            "move right"
    );

    private PlayerBatchCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildPlayerBatchRoot());
        dispatcher.register(buildPbAlias());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPlayerBatchRoot() {
        return buildRoot("playerbatch");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPbAlias() {
        return buildRoot("pb");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String root) {
        return Commands.literal(root)
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.literal("PlayerBatch is command-only now. Use /" + root + " help."),
                            false
                    );
                    return 1;
                })
                .then(Commands.literal("wand")
                        .executes(context -> PlayerBatchService.giveSelectionWand(context.getSource())))
                .then(Commands.literal("cancel")
                        .executes(context -> PlayerBatchService.cancelBatches(context.getSource())))
                .then(Commands.literal("selection")
                        .then(Commands.literal("clear")
                                .executes(context -> PlayerBatchService.clearSelection(context.getSource())))
                        .then(Commands.literal("list")
                                .executes(context -> PlayerBatchService.listSelection(context.getSource())))
                        .then(Commands.literal("all")
                                .executes(context -> PlayerBatchService.selectAll(context.getSource())))
                        .then(Commands.literal("range")
                                .then(Commands.argument("distance", IntegerArgumentType.integer(1))
                                        .executes(context -> PlayerBatchService.selectWithinRange(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "distance")
                                        ))))
                        .then(Commands.literal("count")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> PlayerBatchService.selectClosest(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "number")
                                        )))))
                .then(Commands.literal("config")
                        .then(Commands.literal("limit")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> PlayerBatchService.setLimit(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "number")
                                        ))))
                        .then(Commands.literal("spawns_per_tick")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> PlayerBatchService.setSpawnsPerTick(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "number")
                                        ))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on")
                                        .executes(context -> PlayerBatchService.setDebug(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> PlayerBatchService.setDebug(context.getSource(), false)))))
                .then(Commands.literal("group")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> PlayerBatchService.createGroup(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> PlayerBatchService.assignSelectionToGroup(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> PlayerBatchService.removeSelectionFromGroup(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> PlayerBatchService.listGroups(context.getSource()))))
                .then(Commands.literal("ai")
                        .then(Commands.literal("set")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AI_MODE_SUGGESTIONS, builder))
                                        .executes(context -> PlayerBatchService.setSelectedAiMode(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "mode")
                                        ))
                                        .then(Commands.literal("all")
                                                .executes(context -> PlayerBatchService.setAllAiMode(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "mode")
                                                )))
                                        .then(Commands.literal("group")
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(context -> PlayerBatchService.setGroupAiMode(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "name"),
                                                                StringArgumentType.getString(context, "mode")
                                                        ))))))
                        .then(Commands.literal("status")
                                .executes(context -> PlayerBatchService.describeSelectedAi(context.getSource()))
                                .then(Commands.literal("group")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> PlayerBatchService.describeGroupAi(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name")
                                                ))))))
                .then(Commands.literal("combat")
                        .then(Commands.literal("on")
                                .executes(context -> PlayerBatchService.setSelectedCombatMode(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> PlayerBatchService.setSelectedCombatMode(context.getSource(), false))))
                .then(Commands.literal("test")
                        .then(Commands.literal("list")
                                .executes(context -> PlayerBatchService.showTestHelp(context.getSource())))
                        .then(Commands.literal("help")
                                .executes(context -> PlayerBatchService.showTestHelp(context.getSource())))
                        .then(Commands.literal("all")
                                .executes(context -> PlayerBatchService.runFullSystemTest(context.getSource())))
                        .then(Commands.literal("goto")
                                .then(Commands.literal("coords")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(context -> PlayerBatchService.testGotoPosition(
                                                                        context.getSource(),
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z")
                                                                ))))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(context -> PlayerBatchService.testGotoEntity(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "target")
                                                )))))
                        .then(Commands.literal("stop")
                                .executes(context -> PlayerBatchService.clearTestGoto(context.getSource())))
                        .then(Commands.literal("wall")
                                .executes(context -> PlayerBatchService.buildTestStructure(context.getSource(), "wall")))
                        .then(Commands.literal("gap")
                                .executes(context -> PlayerBatchService.buildTestStructure(context.getSource(), "gap")))
                        .then(Commands.literal("climb")
                                .executes(context -> PlayerBatchService.buildTestStructure(context.getSource(), "climb")))
                        .then(Commands.literal("course")
                                .executes(context -> PlayerBatchService.buildTestStructure(context.getSource(), "course")))
                        .then(Commands.literal("heal")
                                .executes(context -> PlayerBatchService.dropHealingTestPack(context.getSource())))
                        .then(Commands.literal("drop")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.ITEM.keySet().stream().map(Object::toString), builder
                                        ))
                                        .executes(context -> PlayerBatchService.dropTestItem(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "item"),
                                                1
                                        ))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> PlayerBatchService.dropTestItem(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "item"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                ))))))
                .then(Commands.literal("gear")
                        .then(Commands.literal("item")
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SLOT_SUGGESTIONS, builder))
                                        .then(Commands.argument("item", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        BuiltInRegistries.ITEM.keySet().stream().map(Object::toString), builder
                                                ))
                                                .executes(context -> PlayerBatchService.applySelectedItem(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "slot"),
                                                        StringArgumentType.getString(context, "item"),
                                                        1
                                                ))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> PlayerBatchService.applySelectedItem(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "slot"),
                                                                StringArgumentType.getString(context, "item"),
                                                                IntegerArgumentType.getInteger(context, "count")
                                                        ))))))
                        .then(Commands.literal("effect")
                                .then(Commands.argument("effect", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.MOB_EFFECT.keySet().stream().map(Object::toString), builder
                                        ))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(context -> PlayerBatchService.applySelectedEffect(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "effect"),
                                                        IntegerArgumentType.getInteger(context, "seconds"),
                                                        0
                                                ))
                                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .executes(context -> PlayerBatchService.applySelectedEffect(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "effect"),
                                                                IntegerArgumentType.getInteger(context, "seconds"),
                                                                IntegerArgumentType.getInteger(context, "amplifier")
                                                        ))))))
                        .then(Commands.literal("clear_effects")
                                .executes(context -> PlayerBatchService.clearSelectedEffects(context.getSource()))))
                .then(Commands.literal("run")
                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(allActionSuggestions(), builder))
                                .executes(context -> PlayerBatchService.runSelectedAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "action")
                                ))))
                .then(Commands.literal("target")
                        .then(Commands.literal("look")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> PlayerBatchService.lookSelectionAt(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        )))))
                .then(Commands.literal("repair")
                        .then(Commands.literal("tags")
                                .executes(context -> PlayerBatchService.fixBotTags(context.getSource()))))
                .then(Commands.literal("teleport")
                        .then(Commands.literal("selected")
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(DIRECTION_SUGGESTIONS, builder))
                                        .then(Commands.argument("block", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString),
                                                        builder
                                                ))
                                                .executes(context -> PlayerBatchService.teleportSelection(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "direction"),
                                                        StringArgumentType.getString(context, "block")
                                                ))))))
                .then(Commands.literal("spawn")
                        .then(summonArguments()))
                .then(Commands.literal("presets")
                        .then(Commands.literal("combat")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> PlayerBatchService.summonCombatPreset(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                ""
                                        ))
                                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> suggestCombatPresetOptions(builder))
                                                .executes(context -> PlayerBatchService.summonCombatPreset(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        StringArgumentType.getString(context, "options")
                                                )))))
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.literal("combat")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                                                .suggests((context, builder) -> suggestCombatPresetOptions(builder))
                                                                .executes(context -> PlayerBatchService.saveCombatPreset(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                        StringArgumentType.getString(context, "options")
                                                                )))))))
                        .then(Commands.literal("use")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(CombatPresetStore.names(), builder))
                                        .executes(context -> PlayerBatchService.summonSavedCombatPreset(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                null
                                        ))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> PlayerBatchService.summonSavedCombatPreset(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                )))))
                        .then(Commands.literal("list")
                                .executes(context -> PlayerBatchService.listCombatPresets(context.getSource()))))
                .then(Commands.literal("kits")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> PlayerBatchService.saveKit(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(KitStore.names(), builder))
                                        .executes(context -> PlayerBatchService.loadKit(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("self")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(KitStore.names(), builder))
                                        .executes(context -> PlayerBatchService.loadKitSelf(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    List<String> names = KitStore.names();
                                    if (names.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("No saved kits yet."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Saved kits: " + String.join(", ", names)), false);
                                    return names.size();
                                })))
                .then(Commands.literal("help")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("PlayerBatch command guide:"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb spawn <count> <setup>  | summon bots"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb selection <all|range|count|list|clear>  | manage selected bots"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb run <action>  | run an action on selected bots"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb gear item/effect/clear_effects  | edit selected bot gear/effects"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb target look <player>  | make selected bots face a player"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb teleport selected <direction> <block>  | move selected bots near blocks"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb presets use <name> [count]  | use a saved combat preset"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb kits load <name>  | load a saved kit onto selected bots"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb test all  | run the full system test"), false);
                            context.getSource().sendSuccess(() -> Component.literal("/pb config <limit|spawns_per_tick|debug>  | tune PlayerBatch settings"), false);
                            return 1;
                        }));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> summonArguments() {
        return Commands.argument("count", IntegerArgumentType.integer(1))
                .executes(context -> PlayerBatchService.requestSummon(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count"),
                        ""
                ))
                .then(Commands.argument("names", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestSummonSetup(builder))
                        .executes(context -> PlayerBatchService.requestSummonAdvanced(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "count"),
                                StringArgumentType.getString(context, "names")
                        )));
    }

    private static CompletableFuture<Suggestions> suggestCombatPresetOptions(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int optionStart = findActiveOptionStart(remaining);
        SuggestionsBuilder optionBuilder = builder.createOffset(builder.getStart() + optionStart);
        String optionInput = remaining.substring(0, optionStart) + remaining.substring(optionStart).toLowerCase(Locale.ROOT);
        for (String suggestion : CombatPresetParser.suggestOptions(optionInput)) {
            optionBuilder.suggest(suggestion);
        }
        return optionBuilder.buildFuture();
    }

    private static int findActiveOptionStart(String remaining) {
        int lastSpace = Math.max(remaining.lastIndexOf(' '), remaining.lastIndexOf('\t'));
        return Math.max(0, lastSpace + 1);
    }

    private static CompletableFuture<Suggestions> suggestSummonSetup(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int tokenStart = findActiveOptionStart(remaining);
        String active = remaining.substring(tokenStart).trim();
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenStart);
        List<String> tokens = splitSummonTokens(remaining);
        boolean endsWithWhitespace = !remaining.isEmpty() && Character.isWhitespace(remaining.charAt(remaining.length() - 1));
        int activeIndex = endsWithWhitespace ? tokens.size() : Math.max(0, tokens.size() - 1);
        boolean hasFormation = tokens.stream().limit(Math.max(0, tokens.size() - (endsWithWhitespace ? 0 : 1)))
                .map(String::toLowerCase)
                .anyMatch(PlayerBatchCommand::isFormationToken);

        String lowerActive = active.toLowerCase(Locale.ROOT);
        if (lowerActive.startsWith("-")) {
            for (String option : allSummonArgumentSuggestions(remaining.toLowerCase(Locale.ROOT))) {
                tokenBuilder.suggest(option);
            }
            return tokenBuilder.buildFuture();
        }
        if (lowerActive.startsWith("kit") || lowerActive.startsWith("-kit")) {
            for (String kitName : KitStore.names()) {
                tokenBuilder.suggest("kit{" + kitName + "}");
            }
            return tokenBuilder.buildFuture();
        }
        if (!hasFormation && activeIndex >= 1 || looksLikeFormationPrefix(lowerActive)) {
            for (String formation : allFormationSuggestions()) {
                if (lowerActive.isEmpty() || formation.startsWith(lowerActive)) {
                    tokenBuilder.suggest(formation);
                }
            }
            return tokenBuilder.buildFuture();
        }
        if (hasFormation && (lowerActive.isEmpty() || lowerActive.startsWith("-"))) {
            for (String option : allSummonArgumentSuggestions(remaining.toLowerCase(Locale.ROOT))) {
                tokenBuilder.suggest(option);
            }
            for (String kitName : KitStore.names()) {
                tokenBuilder.suggest("kit{" + kitName + "}");
            }
            return tokenBuilder.buildFuture();
        }
        return tokenBuilder.buildFuture();
    }

    private static List<String> splitSummonTokens(String rawSetup) {
        if (rawSetup == null || rawSetup.isBlank()) {
            return List.of();
        }
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        for (char character : rawSetup.toCharArray()) {
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

    private static boolean isFormationToken(String token) {
        return allFormationSuggestions().contains(token) || token.equals("single block") || token.equals("singleblock");
    }

    private static boolean looksLikeFormationPrefix(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (String formation : allFormationSuggestions()) {
            if (formation.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> allFormationSuggestions() {
        java.util.LinkedHashSet<String> suggestions = new java.util.LinkedHashSet<>(FORMATION_SUGGESTIONS);
        suggestions.addAll(PlayerBatchExtensionManager.formationIds());
        return List.copyOf(suggestions);
    }

    private static List<String> allSummonArgumentSuggestions(String currentInput) {
        java.util.LinkedHashSet<String> suggestions = new java.util.LinkedHashSet<>(CombatPresetParser.suggestOptions(currentInput));
        suggestions.add("-blocks{cobblestone*64}");
        suggestions.add("-blocks{oak_planks*32,cobblestone*64}");
        for (PlayerBatchArgument argument : PlayerBatchExtensionManager.arguments()) {
            suggestions.addAll(argument.suggestionExamples());
        }
        return List.copyOf(suggestions);
    }

    private static List<String> allActionSuggestions() {
        java.util.LinkedHashSet<String> suggestions = new java.util.LinkedHashSet<>(ACTION_SUGGESTIONS);
        for (PlayerBatchAction action : PlayerBatchExtensionManager.actions()) {
            suggestions.add(action.id());
            suggestions.addAll(action.aliases());
        }
        return List.copyOf(suggestions);
    }
}

