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
        RenderSection savedSection;
        SectionLayout savedLayout;
        CanvasDrawing canvas;
        CanvasTransform transform = CanvasTransform.IDENTITY;
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
        closeCanvas();
        current.layout = layout;
        current.section = null;
    }

    void beginSection(SectionLayout layout) { beginSection(layout, null, null); }

    void beginSection(SectionLayout layout, String id, Double duration) {
        closeCanvas();
        validateOptions(id, duration);
        if (id != null) entries.removeIf(entry -> id.equals(entry.id()));
        current.layout = layout;
        current.section = new RenderSection(layout);
        entries.add(new Entry(current.section, current.owner, id, duration, now()));
    }

    void hideSection(String id) {
        closeCanvas();
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
        if (current.canvas != null) {
            if (endsRow) throw new IllegalArgumentException("Canvas does not support automatic line breaks");
            current.canvas.add(new CanvasDrawing.Content(element, current.transform));
            return;
        }
        ensureSection();
        current.section.add(element, endsRow);
    }

    boolean isCanvas() { return current.canvas != null; }

    void transform(CanvasTransform transform) { current.transform = transform; }

    void command(CanvasDrawing.Command command) {
        if (current.canvas == null) throw new IllegalStateException("Select a canvas before drawing shapes");
        current.canvas.add(command);
    }

    void beginCanvas(SectionLayout layout, float width, float height, float scale,
                     boolean clip, boolean visible, boolean inline, String id, Double duration) {
        CanvasGeometry.length(width); CanvasGeometry.length(height);
        if (width == 0 || height == 0) throw new IllegalArgumentException("Canvas dimensions must be greater than zero");
        validateOptions(id, duration);
        closeCanvas();
        CanvasDrawing drawing = new CanvasDrawing(width, height, scale, clip, visible);
        RenderElement.Canvas element = new RenderElement.Canvas(drawing, 0, 0);
        if (inline) {
            ensureSection();
            current.section.add(element, false);
        }
        RenderSection previous = current.section;
        SectionLayout previousLayout = current.layout;
        if (!inline) {
            beginSection(layout, id, duration);
            current.section.canvas(element);
        }
        current.savedSection = previous;
        current.savedLayout = previousLayout;
        current.canvas = drawing;
        current.transform = CanvasTransform.IDENTITY;
    }

    void closeCanvas() {
        if (current.canvas == null) return;
        current.section = current.savedSection;
        current.layout = current.savedLayout;
        current.savedSection = null;
        current.savedLayout = null;
        current.canvas = null;
        current.transform = CanvasTransform.IDENTITY;
    }

    void newLine() {
        if (isCanvas()) throw new IllegalArgumentException("Canvas does not support line breaks");
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
