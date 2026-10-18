package dev.ifair.luahud.render;

public enum Anchor {
    TOP_LEFT(Horizontal.LEFT, Vertical.TOP),
    TOP_CENTER(Horizontal.CENTER, Vertical.TOP),
    TOP_RIGHT(Horizontal.RIGHT, Vertical.TOP),
    BOTTOM_LEFT(Horizontal.LEFT, Vertical.BOTTOM),
    BOTTOM_CENTER(Horizontal.CENTER, Vertical.BOTTOM),
    BOTTOM_RIGHT(Horizontal.RIGHT, Vertical.BOTTOM);

    private enum Horizontal { LEFT, CENTER, RIGHT }
    private enum Vertical { TOP, BOTTOM }

    private final Horizontal horizontal;
    private final Vertical vertical;

    Anchor(Horizontal horizontal, Vertical vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static Anchor fromString(String value) {
        if (value == null) return TOP_LEFT;
        return switch (value) {
            case "topLeft" -> TOP_LEFT;
            case "topCenter" -> TOP_CENTER;
            case "topRight" -> TOP_RIGHT;
            case "bottomLeft" -> BOTTOM_LEFT;
            case "bottomCenter" -> BOTTOM_CENTER;
            case "bottomRight" -> BOTTOM_RIGHT;
            default -> throw new IllegalArgumentException("Unknown anchor: " + value);
        };
    }

    public RowAlignment defaultRowAlignment() {
        return switch (horizontal) {
            case LEFT -> RowAlignment.LEFT;
            case CENTER -> RowAlignment.CENTER;
            case RIGHT -> RowAlignment.RIGHT;
        };
    }

    public int defaultOffsetX() {
        return 0;
    }

    int resolveX(int screenWidth, int contentWidth, int offsetX) {
        return switch (horizontal) {
            case LEFT -> offsetX;
            case CENTER -> (screenWidth - contentWidth) / 2 + offsetX;
            case RIGHT -> screenWidth - offsetX - contentWidth;
        };
    }

    int resolveY(int screenHeight, int contentHeight, int offsetY) {
        return vertical == Vertical.TOP ? offsetY : screenHeight - offsetY - contentHeight;
    }

    boolean bottom() { return vertical == Vertical.BOTTOM; }
}
