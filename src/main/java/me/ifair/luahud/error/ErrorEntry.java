package me.ifair.luahud.error;

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
}
