package dev.ifair.luahud.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

final class RenderQueue {
    private record Entry(RenderSection section, Object owner, String id, Double duration, long startedAt) {}
    private static final class Invocation {
        final Object owner;
        RenderSection section;
        SectionLayout layout = SectionLayout.defaults(Anchor.TOP_LEFT, 2, 2);
        Invocation(Object owner) { this.owner = owner; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Deque<Invocation> invocations = new ArrayDeque<>();
    private final LongSupplier nanoTime;
    private Invocation current = new Invocation(new Object());
    private boolean gameTime;
    private long gameNanos;

    RenderQueue() { this(System::nanoTime); }
    RenderQueue(LongSupplier nanoTime) { this.nanoTime = Objects.requireNonNull(nanoTime); }

    void configureClock(boolean gameTime) {
        clear();
        this.gameTime = gameTime;
        gameNanos = 0;
    }

    void tickClock() {
        if (gameTime) gameNanos += 50_000_000L;
    }

    private long now() { return gameTime ? gameNanos : nanoTime.getAsLong(); }

    void clear() {
        entries.clear();
        invocations.clear();
        current = new Invocation(new Object());
    }

    void beginInvocation(Object owner) {
        entries.removeIf(entry -> entry.owner() == owner && entry.duration() == null);
        invocations.push(current);
        current = new Invocation(owner);
    }

    void endInvocation() {
        current = invocations.pop();
    }

    void selectDefault(SectionLayout layout) {
        current.layout = layout;
        current.section = null;
    }

    void beginSection(SectionLayout layout) { beginSection(layout, null, null); }

    void beginSection(SectionLayout layout, String id, Double duration) {
        validateOptions(id, duration);
        if (id != null) entries.removeIf(entry -> id.equals(entry.id()));
        current.layout = layout;
        current.section = new RenderSection(layout);
        entries.add(new Entry(current.section, current.owner, id, duration, now()));
    }

    void hideSection(String id) {
        if (id != null) entries.removeIf(entry -> id.equals(entry.id()));
        current.section = null;
    }

    static void validateOptions(String id, Double duration) {
        if (id != null && id.isBlank()) throw new IllegalArgumentException("Section id must not be blank");
        if (duration != null && (!Double.isFinite(duration) || duration <= 0)) {
            throw new IllegalArgumentException("Section duration must be finite and greater than zero");
        }
    }

    private void ensureSection() {
        if (current.section == null) beginSection(current.layout);
    }

    void add(RenderElement element, boolean endsRow) {
        ensureSection();
        current.section.add(element, endsRow);
    }

    void newLine() {
        ensureSection();
        current.section.newLine();
    }

    boolean expire() {
        long time = now();
        return entries.removeIf(entry -> entry.duration() != null
                && (time - entry.startedAt()) / 1_000_000_000.0 >= entry.duration());
    }

    List<RenderSection> sections() {
        return entries.stream().map(Entry::section).toList();
    }
}
