package com.zahen.playerbatch;

import carpet.CarpetExtension;
import com.mojang.brigadier.CommandDispatcher;
import com.zahen.playerbatch.command.PlayerBatchCommand;
import com.zahen.playerbatch.core.PlayerBatchService;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public class CarpetExtensionImpl implements CarpetExtension {
    @Override
    public String version() {
        return PlayerBatch.MOD_ID;
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        PlayerBatchCommand.register(dispatcher);
    }

    @Override
    public void onTick(MinecraftServer server) {
        PlayerBatchService.tick(server);
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        PlayerBatchService.clear(server);
    }
}

