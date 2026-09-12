package me.ifair.luahud.render;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderBufferTest {
    @Test void scaledIconsReportTheirActualHeight() {
        RenderElement.Texture texture = new RenderElement.Texture(
                Identifier.of("luahud", "icon.png"), 0.5f, 0, 0, 0);

        assertEquals(6, RenderElementMeasurer.height(texture, null, 1f));
        assertEquals(3, RenderElementMeasurer.height(texture, null, 0.5f));
    }

    @Test void newlineClosesTheCurrentRowAndCreatesEmptyRowsAtRowBoundaries() {
        RenderQueue queue = new RenderQueue();
        queue.beginSection(SectionLayout.defaults(Anchor.TOP_LEFT, 2, 2));
        queue.add(new RenderElement.Text("first", 0, 0, false, 0, 0), false);

        queue.newLine();
        queue.newLine();

        assertEquals(2, queue.sections().getFirst().rows().size());
        assertEquals(1, queue.sections().getFirst().rows().get(0).elements().size());
        assertEquals(0, queue.sections().getFirst().rows().get(1).elements().size());
    }

    @Test void newlineCountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> RenderBuffer.newLine(0));
    }

    @Test void emptyStacksOnlyReserveWidthWhenDrawAirIsEnabled() {
        assertEquals(0, RenderElementMeasurer.stackWidth(true, 1f, 1f, false));
        assertEquals(12, RenderElementMeasurer.stackWidth(true, 1f, 1f, true));
        assertEquals(24, RenderElementMeasurer.stackWidth(true, 2f, 1f, true));
        assertEquals(36, RenderElementMeasurer.stackWidth(true, 1.5f, 2f, true));
    }

    @Test void topAnchorsKeepTheIconTopEdgeFixedWhileScaling() {
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_LEFT, 20, 12, 12));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_LEFT, 20, 24, 12));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_CENTER, 20, 24, 12));
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.TOP_RIGHT, 20, 24, 12));
    }

    @Test void bottomAnchorsKeepTheIconBottomEdgeFixedWhileScaling() {
        assertEquals(19, RenderPainter.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 12, 12));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_LEFT, 20, 24, 12));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_CENTER, 20, 24, 12));
        assertEquals(7, RenderPainter.anchoredIconY(Anchor.BOTTOM_RIGHT, 20, 24, 12));
    }
}
