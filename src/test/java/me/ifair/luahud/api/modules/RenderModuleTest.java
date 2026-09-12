package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaTableValue;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.core.StubGenerator;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.InlineDirection;
import me.ifair.luahud.render.RowAlignment;
import me.ifair.luahud.render.RowDirection;
import me.ifair.luahud.render.SectionState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderModuleTest {
    @Test void itemAndEntityDrawFunctionsAcceptIdsOrModels() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        RenderModule.register(api, models);
        registry.freeze();

        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@param id string | ItemStack Namespaced item ID or ItemStack model"));
        assertTrue(stub.contains("---@param uuid string | Entity Entity UUID or Entity model"));
        assertTrue(stub.contains("function lh.drawEntity(uuid, options) end"));
    }

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

    @Test void sectionScaleMustBePositiveAndFinite() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("scale", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("scale", Double.POSITIVE_INFINITY))));
    }

    @Test void sectionScaleIconScaleAndRowHeightAreIndependentOptions() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of(
                "scale", 2,
                "iconScale", 1.5,
                "rowHeight", 10
        )));

        assertEquals(2f, state.scale());
        assertEquals(1.5f, state.iconScale());
        assertEquals(10, state.rowHeight());
        assertEquals(20, state.layout().scaledRowHeight());
    }

    @Test void iconScaleAndRowHeightMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("iconScale", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("rowHeight", 0))));
    }
}
