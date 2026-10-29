package dev.ifair.luahud.render;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry generation, independent of Minecraft's version-specific submission API. */
public final class CanvasGeometry {
    private CanvasGeometry() {}
    public record Vertex(float x, float y, int color) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
        public Quad {
            double area = (double) a.x * b.y - (double) b.x * a.y
                    + (double) b.x * c.y - (double) c.x * b.y
                    + (double) c.x * d.y - (double) d.x * c.y
                    + (double) d.x * a.y - (double) a.x * d.y;
            // Match Minecraft's GUI winding, including negative-sweep arcs.
            if (area > 0) { Vertex swap = b; b = d; d = swap; }
        }
    }

    public static void length(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException("Length must be finite and between 0 and 1000000");
        }
    }

    private static Vertex vertex(double x, double y, int color) {
        return new Vertex((float) x, (float) y, color);
    }

    public static List<Quad> gradient(float x, float y, float w, float h, int from, int to, boolean horizontal) {
        length(w); length(h);
        if (w == 0 || h == 0) return List.of();
        return List.of(new Quad(vertex(x, y, from), vertex(x, y + h, horizontal ? from : to),
                vertex(x + w, y + h, to), vertex(x + w, y, horizontal ? to : from)));
    }

    public static List<Quad> line(float x1, float y1, float x2, float y2, float thickness, int color) {
        length(thickness);
        double distance = Math.hypot(x2 - x1, y2 - y1);
        if (distance == 0 || thickness == 0) return List.of();
        double dx = -(y2 - y1) / distance * thickness / 2;
        double dy = (x2 - x1) / distance * thickness / 2;
        return List.of(new Quad(vertex(x1 + dx, y1 + dy, color), vertex(x2 + dx, y2 + dy, color),
                vertex(x2 - dx, y2 - dy, color), vertex(x1 - dx, y1 - dy, color)));
    }

    public static List<Quad> arc(float cx, float cy, float radius, float start, float sweep, float thickness, int color) {
        length(radius); length(thickness);
        if (radius == 0 || sweep == 0 || thickness == 0) return List.of();
        float inner = Math.max(0, radius - thickness);
        double radians = Math.toRadians(Math.max(-360, Math.min(360, sweep)));
        // At most 512 segments; a half-GUI-pixel chord error for normal HUD sizes.
        int segments = Math.max(1, Math.min(512, (int) Math.ceil(Math.abs(radians) * Math.sqrt(radius))));
        List<Quad> result = new ArrayList<>(segments);
        double angle = Math.toRadians(start);
        for (int i = 0; i < segments; i++) {
            double a = angle + radians * i / segments;
            double b = angle + radians * (i + 1) / segments;
            result.add(new Quad(vertex(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, color),
                    vertex(cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, color),
                    vertex(cx + Math.cos(b) * inner, cy + Math.sin(b) * inner, color),
                    vertex(cx + Math.cos(a) * inner, cy + Math.sin(a) * inner, color)));
        }
        return result;
    }

    public static List<Quad> rect(float x, float y, float w, float h, float radius,
                                  int color, float border, int borderColor) {
        length(w); length(h); length(radius); length(border);
        if (w == 0 || h == 0) return List.of();
        radius = Math.min(radius, Math.min(w, h) / 2);
        border = Math.min(border, Math.min(w, h) / 2);
        List<Vertex> outer = contour(x, y, w, h, radius, border > 0 ? borderColor : color);
        List<Quad> result = new ArrayList<>();
        if (border > 0) {
            List<Vertex> inner = contour(x + border, y + border, w - border * 2, h - border * 2,
                    Math.max(0, radius - border), borderColor);
            for (int i = 0; i < outer.size(); i++) {
                int next = (i + 1) % outer.size();
                result.add(new Quad(outer.get(i), outer.get(next), inner.get(next), inner.get(i)));
            }
        }
        List<Vertex> fill = contour(x + border, y + border, w - border * 2, h - border * 2,
                Math.max(0, radius - border), color);
        Vertex center = vertex(x + w / 2, y + h / 2, color);
        if (w > border * 2 && h > border * 2) {
            for (int i = 0; i < fill.size(); i++) {
                result.add(new Quad(center, fill.get(i), fill.get((i + 1) % fill.size()), center));
            }
        }
        return result;
    }

    private static List<Vertex> contour(float x, float y, float w, float h, float r, int color) {
        // Matching topology for outer and inner borders, including square corners.
        List<Vertex> result = new ArrayList<>(36);
        for (int corner = 0; corner < 4; corner++) {
            double cx = x + (corner == 0 || corner == 3 ? w - r : r);
            double cy = y + (corner < 2 ? h - r : r);
            for (int i = 0; i <= 8; i++) {
                double angle = (corner * Math.PI / 2) + i * Math.PI / 16;
                result.add(vertex(cx + Math.cos(angle) * r, cy + Math.sin(angle) * r, color));
            }
        }
        return result;
    }
}
