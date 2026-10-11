package dev.ifair.luahud.render;

public enum RowAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public static RowAlignment fromString(String value) {
        return switch (value) {
            case "left" -> LEFT;
            case "center" -> CENTER;
            case "right" -> RIGHT;
            default -> throw new IllegalArgumentException("Unknown row alignment: " + value);
        };
    }
}
