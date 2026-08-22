package me.ifair.luahud.render;

// TODO: Make more cool anchors (center, above hotbar etc.)
public enum Anchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public static Anchor fromString(String value) {
        if (value == null) return TOP_LEFT;
        return switch (value) {
            case "topRight" -> TOP_RIGHT;
            case "bottomLeft" -> BOTTOM_LEFT;
            case "bottomRight" -> BOTTOM_RIGHT;
            default -> TOP_LEFT;
        };
    }

    boolean right() { return this == TOP_RIGHT || this == BOTTOM_RIGHT; }
    boolean bottom() { return this == BOTTOM_LEFT || this == BOTTOM_RIGHT; }
}