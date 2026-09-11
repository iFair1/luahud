package me.ifair.luahud.render;

import java.util.Objects;

public record SectionLayout(
        Anchor anchor,
        int offsetX,
        int offsetY,
        RowAlignment rowAlignment,
        InlineDirection inlineDirection,
        RowDirection rowDirection
) {
    public SectionLayout {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rowAlignment, "rowAlignment");
        Objects.requireNonNull(inlineDirection, "inlineDirection");
        Objects.requireNonNull(rowDirection, "rowDirection");
    }

    public static SectionLayout defaults(Anchor anchor, int offsetX, int offsetY) {
        return new SectionLayout(
                anchor,
                offsetX,
                offsetY,
                anchor.defaultRowAlignment(),
                InlineDirection.LEFT_TO_RIGHT,
                RowDirection.TOP_TO_BOTTOM
        );
    }
}
