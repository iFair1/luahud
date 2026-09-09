package me.ifair.luahud.api.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderModuleTest {
    @Test void numericColorsAreStrictArgb() {
        assertEquals(0x00000000, RenderModule.color(0x0));
        assertEquals(0xFF00FF00, RenderModule.color(0xFF00FF00));
    }

    @Test void sixDigitStringsRemainOpaqueRgb() {
        assertEquals(0xFF00FF00, RenderModule.color("#00FF00"));
        assertEquals(0x00000000, RenderModule.color("#00000000"));
    }
}
