package dev.ifair.luahud.render;

import java.util.Objects;

public record SectionState(
        Anchor anchor,
        int offsetX,
        int offsetY,
        RowAlignment rowAlignment,
        InlineAlignment inlineAlignment,
        InlineDirection inlineDirection,
        RowDirection rowDirection,
        boolean shadow,
        int textColor,
        int backgroundColor,
        float scale,
        float iconScale,
        int rowHeight,
        boolean autoRowHeight,
        int lineSpacing,
        int inlineSpacing
) {
    public SectionState {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineAlignment, "inlineAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        if (!Float.isFinite(iconScale) || iconScale <= 0) {
            throw new IllegalArgumentException("Icon scale must be finite and greater than zero");
        }
        if (rowHeight <= 0) throw new IllegalArgumentException("Row height must be greater than zero");
        if (lineSpacing < 0) throw new IllegalArgumentException("Line spacing cannot be negative");
        if (inlineSpacing < 0) throw new IllegalArgumentException("Inline spacing cannot be negative");
    }

    public SectionState(
            Anchor anchor, int offsetX, int offsetY, RowAlignment rowAlignment,
            InlineAlignment inlineAlignment, InlineDirection inlineDirection, RowDirection rowDirection,
            boolean shadow, int textColor, int backgroundColor, float scale, float iconScale,
            int rowHeight, boolean autoRowHeight, int lineSpacing
    ) {
        this(anchor, offsetX, offsetY, rowAlignment, inlineAlignment, inlineDirection, rowDirection,
                shadow, textColor, backgroundColor, scale, iconScale, rowHeight, autoRowHeight, lineSpacing, 0);
    }

    public static SectionState defaults() {
        Anchor anchor = Anchor.TOP_LEFT;
        return new SectionState(
                anchor,
                anchor.defaultOffsetX(),
                2,
                anchor.defaultRowAlignment(),
                InlineAlignment.CENTER,
                InlineDirection.LEFT_TO_RIGHT,
                RowDirection.TOP_TO_BOTTOM,
                true,
                0xFFFFFFFF,
                0x80000000,
                1.0f,
                1.0f,
                RenderDimensions.DEFAULT_ROW_HEIGHT,
                true,
                RenderDimensions.DEFAULT_LINE_SPACING,
                0
        );
    }

    public SectionLayout layout() {
        return new SectionLayout(
                anchor, offsetX, offsetY, rowAlignment, inlineAlignment, inlineDirection, rowDirection,
                scale, rowHeight, autoRowHeight, lineSpacing, inlineSpacing);
    }
}
