package dev.ifair.luahud.render;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderElementMeasurerTest {
    @Test void scaledIconsReportTheirActualHeight() {
        RenderElement.Texture texture = new RenderElement.Texture(
                Identifier.of("luahud", "icon.png"), 0.5f, 0, 0, 0);

        assertEquals(6, RenderElementMeasurer.height(texture, null, 1f));
        assertEquals(3, RenderElementMeasurer.height(texture, null, 0.5f));
    }

    @Test void emptyStacksOnlyReserveWidthWhenDrawAirIsEnabled() {
        assertEquals(0, RenderElementMeasurer.stackWidth(true, 1f, 1f, false));
        assertEquals(12, RenderElementMeasurer.stackWidth(true, 1f, 1f, true));
        assertEquals(24, RenderElementMeasurer.stackWidth(true, 2f, 1f, true));
        assertEquals(36, RenderElementMeasurer.stackWidth(true, 1.5f, 2f, true));
    }
}
