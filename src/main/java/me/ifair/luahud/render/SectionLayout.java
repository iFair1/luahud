package me.ifair.luahud.render;

import java.util.Objects;

public record SectionLayout(
        Anchor anchor,
        int offsetX,
        int offsetY,
        RowAlignment rowAlignment,
        InlineAlignment inlineAlignment,
        InlineDirection inlineDirection,
        RowDirection rowDirection,
        float scale,
        int rowHeight,
        boolean autoRowHeight,
        int lineSpacing,
        int inlineSpacing
) {
    public SectionLayout {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineAlignment, "inlineAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        if (rowHeight <= 0) throw new IllegalArgumentException("Row height must be greater than zero");
        if (lineSpacing < 0) throw new IllegalArgumentException("Line spacing cannot be negative");
        if (inlineSpacing < 0) throw new IllegalArgumentException("Inline spacing cannot be negative");
    }

    public SectionLayout(
            Anchor anchor, int offsetX, int offsetY, RowAlignment rowAlignment,
            InlineAlignment inlineAlignment, InlineDirection inlineDirection, RowDirection rowDirection,
            float scale, int rowHeight, boolean autoRowHeight, int lineSpacing
    ) {
        this(anchor, offsetX, offsetY, rowAlignment, inlineAlignment, inlineDirection, rowDirection,
                scale, rowHeight, autoRowHeight, lineSpacing, 0);
    }

    public static SectionLayout defaults(Anchor anchor, int offsetX, int offsetY) {
        return new SectionLayout(
                anchor,
                offsetX,
                offsetY,
                anchor.defaultRowAlignment(),
                InlineAlignment.CENTER,
                InlineDirection.LEFT_TO_RIGHT,
                RowDirection.TOP_TO_BOTTOM,
                1.0f,
                RenderDimensions.DEFAULT_ROW_HEIGHT,
                true,
                RenderDimensions.DEFAULT_LINE_SPACING,
                0
        );
    }

    public int scaledInlineSpacing() {
        return RenderDimensions.scaleLength(inlineSpacing, scale);
    }

    public int scaledRowHeight() {
        return RenderDimensions.scaleLength(rowHeight, scale);
    }

    public int spacingAbove() {
        int unscaled = Math.min(lineSpacing, lineSpacing / 2 + 1);
        return RenderDimensions.scaleLength(unscaled, scale);
    }

    public int spacingBelow() {
        int unscaled = lineSpacing - Math.min(lineSpacing, lineSpacing / 2 + 1);
        return RenderDimensions.scaleLength(unscaled, scale);
    }

    public int scaledLineSpacing() {
        return spacingAbove() + spacingBelow();
    }
}
