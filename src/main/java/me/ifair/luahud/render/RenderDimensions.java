package me.ifair.luahud.render;

final class RenderDimensions {
    static final int DEFAULT_ROW_HEIGHT = 12;
    static final int BASE_ICON_SIZE = 12;
    static final int PADDING_LEFT = 2;
    static final int PADDING_RIGHT = 2;

    private RenderDimensions() {}

    static int scaleLength(int length, float scale) {
        if (length == 0) return 0;
        return Math.max(1, Math.round(length * scale));
    }

    static int scaleOffset(int offset, float scale) {
        return Math.round(offset * scale);
    }
}
