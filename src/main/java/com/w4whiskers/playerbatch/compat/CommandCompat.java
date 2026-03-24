package com.w4whiskers.playerbatch.compat;

import com.w4whiskers.playerbatch.PlayerBatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class CommandCompat {
    private CommandCompat() {
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        try {
            Method hasPermission = source.getClass().getMethod("hasPermission", int.class);
            return (boolean) hasPermission.invoke(source, level);
        } catch (ReflectiveOperationException ignored) {
            // Fall through to 1.21.11+ style permission check.
        }

        try {
            Method permissions = source.getClass().getMethod("permissions");
            Object permissionSet = permissions.invoke(source);
            if (permissionSet == null) {
                return false;
            }

            if (permissionSet instanceof Enum<?> enumPermission) {
                return enumPermission.ordinal() >= level;
            }

            Class<?> permissionSetClass = permissionSet.getClass();

            try {
                Object gameMasters = permissionSetClass.getField("LEVEL_GAMEMASTERS").get(null);
                Method check = permissionSetClass.getMethod("check", permissionSetClass);
                return (boolean) check.invoke(gameMasters, permissionSet);
            } catch (ReflectiveOperationException ignored) {
                try {
                    Method ordinal = permissionSetClass.getMethod("ordinal");
                    return ((Number) ordinal.invoke(permissionSet)).intValue() >= level;
                } catch (ReflectiveOperationException ignoredToo) {
                    String text = permissionSet.toString().toUpperCase();
                    return text.contains("GAMEMASTER") || text.contains("OP") || text.contains("ADMIN");
                }
            }
        } catch (ReflectiveOperationException exception) {
            PlayerBatch.LOGGER.error("Failed to evaluate command permissions reflectively", exception);
            return false;
        }
    }

    public static boolean performPrefixedCommand(CommandSourceStack source, String command) {
        Object commands = source.getServer().getCommands();
        try {
            Method named = commands.getClass().getMethod("performPrefixedCommand", CommandSourceStack.class, String.class);
            named.invoke(commands, source, command);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // Fall through to signature-based lookup for production runtimes with remapped method names.
        }

        try {
            Method compatible = Arrays.stream(commands.getClass().getMethods())
                    .filter(method -> method.getParameterCount() == 2)
                    .filter(method -> method.getParameterTypes()[0] == CommandSourceStack.class)
                    .filter(method -> method.getParameterTypes()[1] == String.class)
                    .findFirst()
                    .orElse(null);
            if (compatible != null) {
                Object result = compatible.invoke(commands, source, command);
                if (result instanceof Number number) {
                    return number.intValue() > 0;
                }
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            PlayerBatch.LOGGER.error("Failed to invoke compatible command executor for /{}", command, exception);
            return false;
        }

        try {
            @SuppressWarnings("unchecked")
            CommandDispatcher<CommandSourceStack> dispatcher =
                    (CommandDispatcher<CommandSourceStack>) commands.getClass().getMethod("getDispatcher").invoke(commands);
            ParseResults<CommandSourceStack> parseResults = dispatcher.parse("/" + command, source);
            Method performCommand = Arrays.stream(commands.getClass().getMethods())
                    .filter(method -> method.getParameterCount() == 2)
                    .filter(method -> ParseResults.class.isAssignableFrom(method.getParameterTypes()[0]))
                    .filter(method -> method.getParameterTypes()[1] == String.class)
                    .findFirst()
                    .orElse(null);
            if (performCommand != null) {
                performCommand.invoke(commands, parseResults, "/" + command);
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            PlayerBatch.LOGGER.error("Failed to execute Carpet command /{} via parse fallback", command, exception);
        }

        PlayerBatch.LOGGER.error("Failed to execute Carpet command /{} because no compatible command runner was found", command);
        return false;
    }
}

