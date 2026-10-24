package dev.ifair.luahud.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderBufferTest {
    @BeforeEach
    @AfterEach
    void clearBuffer() {
        RenderBuffer.clear();
    }

    @Test void newlineClosesTheCurrentRowAndCreatesEmptyRowsAtRowBoundaries() {
        RenderBuffer.beginSection(SectionLayout.defaults(Anchor.TOP_LEFT, 2, 2));
        RenderBuffer.addText("first", 0, 0, false, 0, 0, false);

        RenderBuffer.newLine(2);

        assertEquals(2, RenderBuffer.sections().getFirst().rows().size());
        assertEquals(1, RenderBuffer.sections().getFirst().rows().get(0).elements().size());
        assertEquals(0, RenderBuffer.sections().getFirst().rows().get(1).elements().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void newlineCountMustBePositive(int count) {
        assertThrows(IllegalArgumentException.class, () -> RenderBuffer.newLine(count));
    }
}
