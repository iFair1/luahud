package me.ifair.luahud.render;

import java.util.Objects;

public record SectionLayout(
        Anchor anchor,
        int offsetX,
        int offsetY,
        RowAlignment rowAlignment,
        InlineDirection inlineDirection,
        RowDirection rowDirection,
        float scale,
        int rowHeight
) {
    public SectionLayout {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        if (rowHeight <= 0) throw new IllegalArgumentException("Row height must be greater than zero");
    }

    public static SectionLayout defaults(Anchor anchor, int offsetX, int offsetY) {
        return new SectionLayout(
                anchor,
                offsetX,
                offsetY,
                anchor.defaultRowAlignment(),
                InlineDirection.LEFT_TO_RIGHT,
                RowDirection.TOP_TO_BOTTOM,
                1.0f,
                RenderDimensions.DEFAULT_ROW_HEIGHT
        );
    }

    public int scaledRowHeight() {
        return RenderDimensions.scaleLength(rowHeight, scale);
    }
}
