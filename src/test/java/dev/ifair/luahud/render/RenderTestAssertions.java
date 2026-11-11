package dev.ifair.luahud.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class RenderTestAssertions {
    private RenderTestAssertions() {}

    public static void assertFirstText(String expected) {
        var text = (RenderElement.Text) RenderBuffer.sections().getFirst().rows().getFirst().elements().getFirst();
        assertEquals(expected, text.value());
    }
}
