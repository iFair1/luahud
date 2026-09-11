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
        float scale
) {
    public SectionState {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
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
                1.0f
        );
    }

    public SectionLayout layout() {
        return new SectionLayout(anchor, offsetX, offsetY, rowAlignment, inlineDirection, rowDirection);
    }
}
