package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaTableValue;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.core.StubGenerator;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.core.CobaltRuntime;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.InlineAlignment;
import me.ifair.luahud.render.InlineDirection;
import me.ifair.luahud.render.RowAlignment;
import me.ifair.luahud.render.RowDirection;
import me.ifair.luahud.render.SectionState;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(stub.contains("---@param options? EntityOptions Rendering options"));
        assertTrue(stub.contains("---@field pitch? number Vertical viewing angle offset in degrees"));
        assertTrue(stub.contains("---@field yaw? number Horizontal viewing angle offset in degrees"));
        assertTrue(stub.contains("---@field fullBright? boolean Use maximum lighting; defaults to true"));
        assertTrue(stub.contains("---@field useEntityRotation? boolean "
                + "Use the entity's current rotation as the base for yaw and pitch offsets"));
        assertTrue(stub.contains("function lh.drawEntity(uuid, options) end"));
        assertTrue(stub.contains("---@field rowHeight? integer | \"auto\" Fixed unscaled row height"));
        assertTrue(stub.contains("---@field inlineAlign? \"top\" | \"center\" | \"bottom\" "
                + "Vertical alignment of elements within a row; defaults to center"));
        assertTrue(stub.contains("---@param count? integer Number of line breaks; defaults to 1"));
        assertTrue(stub.contains("function lh.newline(count) end"));
        assertTrue(stub.contains("---@field id string Namespaced item ID"));
        assertTrue(stub.contains("---@field count? integer Stack count; defaults to 1 for input tables"));
        assertTrue(stub.contains("---@field damage? integer Current item damage"));
        assertTrue(stub.contains("---@field damageLeft? integer Remaining durability"));
        assertTrue(stub.contains("---@field enchantments? table<string, integer>"));
        assertTrue(stub.contains("---@field maxDamage? integer Maximum durability"));
        assertTrue(stub.contains("---@field newLine? boolean Advance to the next line after drawing; defaults to false"));
        assertEquals(2, count(stub, "---@field newLine? boolean Advance to the next line after drawing; defaults to false"));
    }

    @Test void entityAnglesDefaultNormalizeAndRejectNonFiniteValues() {
        assertEquals(0f, RenderModule.angle(null, "yaw"));
        assertEquals(45f, RenderModule.angle(new LuaTableValue(Map.of("yaw", 405)), "yaw"));
        assertEquals(-45f, RenderModule.angle(new LuaTableValue(Map.of("pitch", -405)), "pitch"));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.angle(new LuaTableValue(Map.of("yaw", Double.NaN)), "yaw"));
    }

    @Test void entityBooleanOptionsHaveStableDefaults() {
        assertTrue(RenderModule.bool(null, "fullBright", true));
        assertFalse(RenderModule.bool(null, "useEntityRotation", false));
        LuaTableValue options = new LuaTableValue(Map.of(
                "fullBright", false,
                "useEntityRotation", true
        ));
        assertFalse(RenderModule.bool(options, "fullBright", true));
        assertTrue(RenderModule.bool(options, "useEntityRotation", false));
    }

    @Test void iconNewLineDefaultsToFalseAndCanBeEnabled() {
        assertFalse(RenderModule.newLine(null));
        assertFalse(RenderModule.newLine(new LuaTableValue(Map.of())));
        assertTrue(RenderModule.newLine(new LuaTableValue(Map.of("newLine", true))));
    }

    private static int count(String value, String part) {
        return (value.length() - value.replace(part, "").length()) / part.length();
    }

    @Test void texturePathsMustBeValidAndReferToAnExistingResource() {
        Identifier expected = Identifier.of("minecraft", "textures/gui/icon.png");

        assertEquals(expected, RenderModule.textureIdentifier(expected.toString(), expected::equals));
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> RenderModule.textureIdentifier("minecraft:Invalid Path", ignored -> true));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> RenderModule.textureIdentifier("minecraft:textures/gui/missing.png", ignored -> false));

        assertTrue(malformed.getMessage().contains("Invalid texture path"));
        assertTrue(missing.getMessage().contains("Texture not found"));
    }

    @Test void invalidTexturePathSurfacesAsALuaError() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        LuaError error = assertThrows(LuaError.class,
                () -> runtime.execute("lh.drawTexture('minecraft:Invalid Path')", "invalid-texture"));

        assertTrue(error.getMessage().contains("Invalid texture path: minecraft:Invalid Path"));
    }

    @Test void itemIdsMustBeValidAndReferToARegisteredItem() {
        Identifier expected = Identifier.of("minecraft", "stone");

        assertEquals(expected, RenderModule.itemIdentifier(expected.toString(), expected::equals));
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> RenderModule.itemIdentifier("minecraft:Invalid ID", ignored -> true));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> RenderModule.itemIdentifier("minecraft:doesntexist", ignored -> false));

        assertTrue(malformed.getMessage().contains("Invalid item ID: minecraft:Invalid ID"));
        assertTrue(missing.getMessage().contains("Item not found: minecraft:doesntexist"));
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
        assertEquals(InlineAlignment.CENTER, state.inlineAlignment());
        assertEquals(RowDirection.TOP_TO_BOTTOM, state.rowDirection());
        assertTrue(state.autoRowHeight());
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
                "inlineAlign", "bottom",
                "inlineDirection", "rightToLeft",
                "rowDirection", "bottomToTop"
        )));

        assertEquals(RowAlignment.LEFT, state.rowAlignment());
        assertEquals(InlineAlignment.BOTTOM, state.inlineAlignment());
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
        assertFalse(state.autoRowHeight());
        assertEquals(20, state.layout().scaledRowHeight());
    }

    @Test void automaticRowHeightIsTheDefaultAndCanBeRequestedExplicitly() {
        SectionState defaults = RenderModule.sectionState(null);
        SectionState explicit = RenderModule.sectionState(new LuaTableValue(Map.of("rowHeight", "auto")));

        assertTrue(defaults.autoRowHeight());
        assertTrue(explicit.autoRowHeight());
        assertEquals(12, explicit.rowHeight());
    }

    @Test void iconScaleAndRowHeightMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("iconScale", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("rowHeight", 0))));
    }
}
