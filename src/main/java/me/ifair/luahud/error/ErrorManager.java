package me.ifair.luahud.error;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ErrorManager {
    public static final String SYSTEM_GROUP = "LuaHUD";
    static final int MAX_DISTINCT_PER_GROUP = 20;

    private static final String OVERFLOW_KEY = "\u0000overflow";
    private static final Map<String, ErrorGroup> profileGroups = new LinkedHashMap<>();
    private static final ErrorGroup systemGroup = new ErrorGroup(SYSTEM_GROUP, true);
    private static final ArrayDeque<Long> pendingNotifications = new ArrayDeque<>();
    private static long nextId = 1;
    private static long structureRevision;

    private ErrorManager() {
    }

    public static synchronized void beginProfileReload() {
        pendingNotifications.removeIf(id -> findMutable(id).map(error -> !error.system).orElse(true));
        profileGroups.clear();
        structureRevision++;
    }

    public static synchronized void reportProfile(String profile, String phase, Throwable error) {
        reportProfile(profile, phase, detail(error));
    }

    public static synchronized void reportProfile(String profile, String phase, String message) {
        String groupName = profile == null || profile.isBlank() ? "Unknown profile" : profile;
        report(profileGroups.computeIfAbsent(groupName, name -> new ErrorGroup(name, false)), phase, message);
    }

    public static synchronized void reportSystem(String component, Throwable error) {
        reportSystem(component, detail(error));
    }

    public static synchronized void reportSystem(String component, String message) {
        report(systemGroup, component, message);
    }

    public static synchronized List<ErrorEntry> snapshot() {
        List<ErrorEntry> result = new ArrayList<>();
        systemGroup.entries.values().forEach(error -> result.add(error.snapshot()));
        profileGroups.values().stream()
                .sorted(Comparator.comparing(group -> group.name, String.CASE_INSENSITIVE_ORDER))
                .forEach(group -> group.entries.values().forEach(error -> result.add(error.snapshot())));
        return List.copyOf(result);
    }

    public static synchronized Optional<ErrorEntry> find(long id) {
        return findMutable(id).map(MutableError::snapshot);
    }

    public static synchronized List<ErrorEntry> drainNotifications() {
        List<ErrorEntry> result = new ArrayList<>();
        while (!pendingNotifications.isEmpty()) {
            findMutable(pendingNotifications.removeFirst()).map(MutableError::snapshot).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    public static synchronized int distinctCount() {
        int count = systemGroup.entries.size();
        for (ErrorGroup group : profileGroups.values()) count += group.entries.size();
        return count;
    }

    public static synchronized long structureRevision() {
        return structureRevision;
    }

    static synchronized void reset() {
        profileGroups.clear();
        systemGroup.entries.clear();
        pendingNotifications.clear();
        nextId = 1;
        structureRevision = 0;
    }

    private static void report(ErrorGroup group, String phase, String rawMessage) {
        String safePhase = phase == null || phase.isBlank() ? "unknown" : phase.trim();
        String normalized = rawMessage == null ? null : normalizeLineEndings(rawMessage);
        String message = normalized == null || normalized.isBlank() ? "Unknown error" : normalized.trim();
        String key = safePhase + '\u0000' + fingerprint(group.system, message);
        MutableError existing = group.entries.get(key);
        if (existing != null) {
            existing.seen();
            return;
        }

        if (countRealErrors(group) >= MAX_DISTINCT_PER_GROUP) {
            MutableError overflow = group.entries.get(OVERFLOW_KEY);
            if (overflow == null) {
                overflow = new MutableError(nextId++, group.name, group.system, "limit",
                        "Additional distinct errors were suppressed");
                group.entries.put(OVERFLOW_KEY, overflow);
                pendingNotifications.addLast(overflow.id);
                structureRevision++;
            } else {
                overflow.seen();
            }
            return;
        }

        MutableError added = new MutableError(nextId++, group.name, group.system, safePhase, message);
        group.entries.put(key, added);
        pendingNotifications.addLast(added.id);
        structureRevision++;
    }

    private static int countRealErrors(ErrorGroup group) {
        return group.entries.containsKey(OVERFLOW_KEY) ? group.entries.size() - 1 : group.entries.size();
    }

    private static String fingerprint(boolean system, String message) {
        if (system) return message;
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline).trim();
    }

    private static Optional<MutableError> findMutable(long id) {
        for (MutableError error : systemGroup.entries.values()) if (error.id == id) return Optional.of(error);
        for (ErrorGroup group : profileGroups.values()) {
            for (MutableError error : group.entries.values()) if (error.id == id) return Optional.of(error);
        }
        return Optional.empty();
    }

    private static String detail(Throwable error) {
        if (error == null) return "Unknown error";
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();

        String normalized = normalizeLineEndings(message);
        int traceback = normalized.indexOf("\nstack traceback:");
        return traceback < 0 ? normalized : normalized.substring(0, traceback);
    }

    private static String normalizeLineEndings(String message) {
        return message.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class ErrorGroup {
        private final String name;
        private final boolean system;
        private final Map<String, MutableError> entries = new LinkedHashMap<>();

        private ErrorGroup(String name, boolean system) {
            this.name = name;
            this.system = system;
        }
    }

    private static final class MutableError {
        private final long id;
        private final String group;
        private final boolean system;
        private final String phase;
        private final String message;
        private final long firstSeen;
        private int count = 1;
        private long lastSeen;

        private MutableError(long id, String group, boolean system, String phase, String message) {
            this.id = id;
            this.group = group;
            this.system = system;
            this.phase = phase;
            this.message = message;
            this.firstSeen = System.currentTimeMillis();
            this.lastSeen = this.firstSeen;
        }

        private void seen() {
            if (count < Integer.MAX_VALUE) count++;
            lastSeen = System.currentTimeMillis();
        }

        private ErrorEntry snapshot() {
            return new ErrorEntry(id, group, system, phase, message, count, firstSeen, lastSeen);
        }
    }
}
