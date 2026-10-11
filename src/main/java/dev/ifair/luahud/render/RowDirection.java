package dev.ifair.luahud.render;

public enum RowDirection {
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP;

    public static RowDirection fromString(String value) {
        return switch (value) {
            case "topToBottom" -> TOP_TO_BOTTOM;
            case "bottomToTop" -> BOTTOM_TO_TOP;
            default -> throw new IllegalArgumentException("Unknown row direction: " + value);
        };
    }
}
