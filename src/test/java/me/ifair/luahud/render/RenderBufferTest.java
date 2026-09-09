package me.ifair.luahud.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderBufferTest {
    @Test void topAnchorsKeepTheIconTopEdgeFixedWhileScaling() {
        assertEquals(19, RenderBuffer.anchoredIconY(Anchor.TOP_LEFT, 20, 12));
        assertEquals(19, RenderBuffer.anchoredIconY(Anchor.TOP_LEFT, 20, 24));
        assertEquals(19, RenderBuffer.anchoredIconY(Anchor.TOP_RIGHT, 20, 24));
    }

    @Test void bottomAnchorsKeepTheIconBottomEdgeFixedWhileScaling() {
        assertEquals(19, RenderBuffer.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 12));
        assertEquals(7, RenderBuffer.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 24));
        assertEquals(7, RenderBuffer.anchoredIconY(Anchor.BOTTOM_RIGHT, 20, 24));
    }
}
