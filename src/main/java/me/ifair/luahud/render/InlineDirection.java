package me.ifair.luahud.render;

public enum InlineDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT;

    public static InlineDirection fromString(String value) {
        return switch (value) {
            case "leftToRight" -> LEFT_TO_RIGHT;
            case "rightToLeft" -> RIGHT_TO_LEFT;
            default -> throw new IllegalArgumentException("Unknown inline direction: " + value);
        };
    }
}
