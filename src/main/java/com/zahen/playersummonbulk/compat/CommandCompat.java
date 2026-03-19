package com.zahen.playersummonbulk.compat;

import com.zahen.playersummonbulk.PlayerSummonBulk;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

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
            PlayerSummonBulk.LOGGER.error("Failed to evaluate command permissions reflectively", exception);
            return false;
        }
    }

    public static boolean performPrefixedCommand(CommandSourceStack source, String command) {
        try {
            Method method = source.getServer().getCommands().getClass()
                    .getMethod("performPrefixedCommand", CommandSourceStack.class, String.class);
            Object result = method.invoke(source.getServer().getCommands(), source, command);
            if (result instanceof Number number) {
                return number.intValue() > 0;
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            PlayerSummonBulk.LOGGER.error("Failed to execute Carpet command /{}", command, exception);
            return false;
        }
    }
}
