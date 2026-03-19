package com.zahen.playerbatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.zahen.playerbatch.core.PlayerBatchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PlayerBatchCommand {
    private static final List<String> DIRECTION_SUGGESTIONS = List.of("up", "below", "north", "south", "east", "west");
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
        dispatcher.register(buildPlayerSummonAlias());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPlayerBatchRoot() {
        return Commands.literal("playerbatch")
                .executes(context -> PlayerBatchService.openGui(context.getSource()))
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
}

