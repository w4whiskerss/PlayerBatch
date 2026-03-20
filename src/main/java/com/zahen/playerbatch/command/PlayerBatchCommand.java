package com.zahen.playerbatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.zahen.playerbatch.config.CombatPresetStore;
import com.zahen.playerbatch.config.KitStore;
import com.zahen.playerbatch.core.BotAiMode;
import com.zahen.playerbatch.core.PlayerBatchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
        dispatcher.register(buildPlayerSummonAlias());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPlayerBatchRoot() {
        return buildRoot("playerbatch");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPbAlias() {
        return buildRoot("pb");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String root) {
        return Commands.literal(root)
                .executes(context -> PlayerBatchService.openGui(context.getSource()))
                .then(Commands.literal("gui")
                        .executes(context -> PlayerBatchService.openGui(context.getSource())))
                .then(Commands.literal("wand")
                        .executes(context -> PlayerBatchService.giveSelectionWand(context.getSource())))
                .then(Commands.literal("clearselection")
                        .executes(context -> PlayerBatchService.clearSelection(context.getSource())))
                .then(Commands.literal("listselection")
                        .executes(context -> PlayerBatchService.listSelection(context.getSource())))
                .then(Commands.literal("select")
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
                .then(Commands.literal("limit")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(context -> PlayerBatchService.setLimit(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "number")
                                ))))
                .then(Commands.literal("spawnspertick")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(context -> PlayerBatchService.setSpawnsPerTick(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "number")
                                ))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                                .executes(context -> PlayerBatchService.setDebug(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> PlayerBatchService.setDebug(context.getSource(), false))))
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
                .then(Commands.literal("customize")
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
                        .then(Commands.literal("clearEffects")
                                .executes(context -> PlayerBatchService.clearSelectedEffects(context.getSource()))))
                .then(Commands.literal("command")
                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ACTION_SUGGESTIONS, builder))
                                .executes(context -> PlayerBatchService.runSelectedAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "action")
                                ))))
                .then(Commands.literal("tp")
                        .then(Commands.literal("type=wand:selected")
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
                .then(Commands.literal("summon")
                        .then(summonArguments()))
                .then(Commands.literal("preset")
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
                        .then(Commands.literal("summon")
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
                .then(Commands.literal("kit")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> PlayerBatchService.saveKit(
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
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Use /playerbatch summon <count> {optional,names} or /playersummon <count> {optional,names}."),
                                    false
                            );
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPlayerSummonAlias() {
        return Commands.literal("playersummon").then(summonArguments());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> summonArguments() {
        return Commands.argument("count", IntegerArgumentType.integer(1))
                .executes(context -> PlayerBatchService.requestSummon(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count"),
                        ""
                ))
                .then(Commands.argument("names", StringArgumentType.greedyString())
                        .executes(context -> PlayerBatchService.requestSummon(
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
}

