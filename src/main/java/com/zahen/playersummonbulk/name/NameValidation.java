package com.zahen.playersummonbulk.name;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NameValidation {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern MANY_DIGITS = Pattern.compile(".*\\d{4,}.*");
    private static final Pattern LONG_CONSONANT_CHAIN = Pattern.compile(".*[bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ]{6,}.*");

    private NameValidation() {
    }

    public static boolean isAllowedRequestedName(String candidate) {
        return isValidMinecraftName(candidate) && isRealistic(candidate);
    }

    public static boolean isValidMinecraftName(String candidate) {
        return candidate != null
                && VALID_NAME.matcher(candidate).matches()
                && !candidate.startsWith("MHF_");
    }

    public static boolean isRealistic(String candidate) {
        if (!isValidMinecraftName(candidate)) {
            return false;
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        int digits = 0;
        int vowels = 0;
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (Character.isDigit(character)) {
                digits++;
            }
            if ("aeiou".indexOf(character) >= 0) {
                vowels++;
            }
        }

        if (candidate.length() > 6 && vowels == 0) {
            return false;
        }
        if (digits > Math.max(3, candidate.length() / 2)) {
            return false;
        }
        if (MANY_DIGITS.matcher(candidate).matches()) {
            return false;
        }
        return !LONG_CONSONANT_CHAIN.matcher(candidate).matches();
    }
}
