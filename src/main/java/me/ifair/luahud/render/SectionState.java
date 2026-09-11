package me.ifair.luahud.render;

import java.util.Objects;

public record SectionState(
        Anchor anchor,
        int offsetX,
        int offsetY,
        RowAlignment rowAlignment,
        InlineDirection inlineDirection,
        RowDirection rowDirection,
        boolean shadow,
        int textColor,
        int backgroundColor,
        float scale,
        float iconScale,
        int rowHeight
) {
    public SectionState {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        if (!Float.isFinite(iconScale) || iconScale <= 0) {
            throw new IllegalArgumentException("Icon scale must be finite and greater than zero");
        }
        if (rowHeight <= 0) throw new IllegalArgumentException("Row height must be greater than zero");
    }

    public static SectionState defaults() {
        Anchor anchor = Anchor.TOP_LEFT;
        return new SectionState(
                anchor,
                anchor.defaultOffsetX(),
                2,
                anchor.defaultRowAlignment(),
                InlineDirection.LEFT_TO_RIGHT,
                RowDirection.TOP_TO_BOTTOM,
                true,
                0xFFFFFFFF,
                0x80000000,
                1.0f,
                1.0f,
                RenderDimensions.DEFAULT_ROW_HEIGHT
        );
    }

    public SectionLayout layout() {
        return new SectionLayout(
                anchor, offsetX, offsetY, rowAlignment, inlineDirection, rowDirection, scale, rowHeight);
    }
}
