package dev.ifair.luahud.render;

import java.util.Objects;

public record SectionState(
        SectionLayout layout,
        boolean shadow,
        int textColor,
        int backgroundColor,
        float iconScale,
        float opacity
) {
    public SectionState {
        Objects.requireNonNull(layout, "layout");
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1)
            throw new IllegalArgumentException("Opacity must be finite and between 0 and 1");
        if (!Float.isFinite(iconScale) || iconScale <= 0) {
            throw new IllegalArgumentException("Icon scale must be finite and greater than zero");
        }
    }

    public static SectionState defaults() {
        Anchor anchor = Anchor.TOP_LEFT;
        return new SectionState(
                SectionLayout.defaults(anchor, anchor.defaultOffsetX(), 2),
                true, 0xFFFFFFFF, 0x80000000, 1.0f, 1.0f);
    }
}
