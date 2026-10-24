package dev.ifair.luahud.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ErrorEntry(
        long id,
        String group,
        boolean system,
        String phase,
        String message,
        int count,
        long firstSeen,
        long lastSeen
) {
    /** Whether this immutable snapshot represents the first occurrence of the active error. */
    public boolean isNew() {
        return count == 1;
    }

    private static final Pattern LUA_LOCATION = Pattern.compile("^(?:@)?(?:.*[/\\\\])?[^/\\\\:]+\\.lua:(\\d+):\\s?(.*)$");

    public String source() {
        if (system) return ErrorManager.SYSTEM_GROUP;
        return group.toLowerCase(java.util.Locale.ROOT).endsWith(".lua") ? group : group + ".lua";
    }

    public String line() {
        Matcher matcher = LUA_LOCATION.matcher(firstLine());
        return matcher.matches() ? matcher.group(1) : "—";
    }

    public String displayMessage() {
        int newline = message.indexOf('\n');
        String first = newline < 0 ? message : message.substring(0, newline);
        Matcher matcher = LUA_LOCATION.matcher(first);
        if (!matcher.matches()) return message;

        String rest = newline < 0 ? "" : message.substring(newline);
        return matcher.group(2) + rest;
    }

    public String summary() {
        return displayMessage().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String firstLine() {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
