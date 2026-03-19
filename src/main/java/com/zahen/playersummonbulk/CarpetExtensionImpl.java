package com.zahen.playersummonbulk;

import carpet.CarpetExtension;
import com.mojang.brigadier.CommandDispatcher;
import com.zahen.playersummonbulk.command.PlayerBatchCommand;
import com.zahen.playersummonbulk.core.PlayerBatchService;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public class CarpetExtensionImpl implements CarpetExtension {
    @Override
    public String version() {
        return PlayerSummonBulk.MOD_ID;
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
