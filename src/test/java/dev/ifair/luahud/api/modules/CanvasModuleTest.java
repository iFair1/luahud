package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaTableValue;
import dev.ifair.luahud.render.*;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CanvasModuleTest {
    @BeforeEach void canvas() {
        RenderBuffer.clear();
        RenderBuffer.beginCanvas(SectionLayout.defaults(Anchor.TOP_LEFT, 0, 0),
                100, 100, 1, true, true, false, null, null);
    }
    @AfterEach void clear() { RenderBuffer.clear(); RenderModule.resetSection(); }

    private static void draw(Map<String, Object> options) {
        CanvasModule.drawImage(Identifier.of("test", "image.png"), 64, 32,
                new LuaTableValue(options), CanvasTransform.IDENTITY);
    }

    @Test void supportsNativeSizeFractionalDestinationRegionsAndNineSlice() {
        assertDoesNotThrow(() -> draw(Map.of()));
        assertDoesNotThrow(() -> draw(Map.of("width", 10.5, "height", 0.0)));
        assertDoesNotThrow(() -> draw(Map.of("region", new LuaTableValue(Map.of(
                "x", 32, "y", 0, "width", 32, "height", 32)))));
        assertDoesNotThrow(() -> draw(Map.of("width", 2, "height", 1, "nineSlice", new LuaTableValue(Map.of(
                "left", 8, "right", 8, "top", 8, "bottom", 8)))));
    }

    @Test void rejectsOutOfBoundsAndOverflowingSourceRegions() {
        for (var region : List.of(
                Map.of("x", -1, "y", 0, "width", 1, "height", 1),
                Map.of("x", 0, "y", 0, "width", 65, "height", 32),
                Map.of("x", 0, "y", 0, "width", 64, "height", 0),
                Map.of("x", Integer.MAX_VALUE, "y", 0, "width", Integer.MAX_VALUE, "height", 1))) {
            assertThrows(IllegalArgumentException.class, () -> draw(Map.of("region", new LuaTableValue(Map.copyOf(region)))));
        }
    }

    @Test void rejectsIncompatibleOptionsInvalidBordersAndNegativeDestinationSizes() {
        assertThrows(IllegalArgumentException.class, () -> draw(Map.of(
                "region", new LuaTableValue(Map.of()), "nineSlice", new LuaTableValue(Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> draw(Map.of("nineSlice", new LuaTableValue(Map.of("left", -1)))));
        assertThrows(IllegalArgumentException.class, () -> draw(Map.of("nineSlice", new LuaTableValue(Map.of("left", 64)))));
        assertThrows(IllegalArgumentException.class, () -> draw(Map.of("width", -1)));
        assertThrows(IllegalArgumentException.class, () -> draw(Map.of("height", Double.NaN)));
    }
}
