package com.careerpilot.util;

import java.util.regex.Pattern;

public final class SanitizationUtil {

    // Removes ASCII and Unicode control characters while preserving newline, carriage return, and tab
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    private SanitizationUtil() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String stripped = CONTROL_CHARS.matcher(input).replaceAll("");
        return stripped.trim();
    }
}
