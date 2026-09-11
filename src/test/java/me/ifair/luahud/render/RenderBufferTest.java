package me.ifair.luahud.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderBufferTest {
    @Test void emptyStacksOnlyReserveWidthWhenDrawAirIsEnabled() {
        assertEquals(0, RenderElementMeasurer.stackWidth(true, 1f, false));
        assertEquals(12, RenderElementMeasurer.stackWidth(true, 1f, true));
        assertEquals(24, RenderElementMeasurer.stackWidth(true, 2f, true));
    }

    @Test void topAnchorsKeepTheIconTopEdgeFixedWhileScaling() {
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_LEFT, 20, 12));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_LEFT, 20, 24));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_CENTER, 20, 24));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_RIGHT, 20, 24));
    }

    @Test void bottomAnchorsKeepTheIconBottomEdgeFixedWhileScaling() {
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 12));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 24));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_CENTER, 20, 24));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_RIGHT, 20, 24));
    }
}
