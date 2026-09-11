package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaTableValue;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.InlineDirection;
import me.ifair.luahud.render.RowAlignment;
import me.ifair.luahud.render.RowDirection;
import me.ifair.luahud.render.SectionState;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

    @Test void centerAnchorsDefaultToZeroHorizontalOffsetAndCenteredRows() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of("anchor", "topCenter")));

        assertEquals(Anchor.TOP_CENTER, state.anchor());
        assertEquals(0, state.offsetX());
        assertEquals(RowAlignment.CENTER, state.rowAlignment());
        assertEquals(RowDirection.TOP_TO_BOTTOM, state.rowDirection());
    }

    @Test void rightAnchorsKeepTheirInsetAndDeriveRightRowAlignment() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of("anchor", "bottomRight")));

        assertEquals(2, state.offsetX());
        assertEquals(RowAlignment.RIGHT, state.rowAlignment());
    }

    @Test void sectionDirectionsAndAlignmentCanBeOverridden() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of(
                "anchor", "bottomCenter",
                "rowAlign", "left",
                "inlineDirection", "rightToLeft",
                "rowDirection", "bottomToTop"
        )));

        assertEquals(RowAlignment.LEFT, state.rowAlignment());
        assertEquals(InlineDirection.RIGHT_TO_LEFT, state.inlineDirection());
        assertEquals(RowDirection.BOTTOM_TO_TOP, state.rowDirection());
    }
}
