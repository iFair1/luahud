package me.ifair.luahud.render;

public enum InlineAlignment {
    TOP,
    CENTER,
    BOTTOM;

    public static InlineAlignment fromString(String value) {
        return switch (value) {
            case "top" -> TOP;
            case "center" -> CENTER;
            case "bottom" -> BOTTOM;
            default -> throw new IllegalArgumentException("Unknown inline alignment: " + value);
        };
    }
}
