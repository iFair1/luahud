package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaTableValue;
import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.core.StubGenerator;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.core.CobaltRuntime;
import dev.ifair.luahud.render.Anchor;
import dev.ifair.luahud.render.InlineAlignment;
import dev.ifair.luahud.render.InlineDirection;
import dev.ifair.luahud.render.RowAlignment;
import dev.ifair.luahud.render.RowDirection;
import dev.ifair.luahud.render.RenderBuffer;
import dev.ifair.luahud.render.SectionState;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderModuleTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void sectionStateDoesNotLeakBetweenProfileCallbacks() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        var api = registry.registrar("luahud");
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry, RenderModule::resetSection);
        runtime.start();

        try {
            runtime.execute("lh.on('tick', function() lh.section({anchor='topRight'}) end)", "profile-a");
            runtime.execute("lh.on('tick', function() lh.draw('B') end)", "profile-b");
            runtime.fireEvent("tick");

            SectionState state = RenderModule.currentSectionState();
            assertEquals(Anchor.TOP_LEFT, state.layout().anchor());
            assertEquals(RowAlignment.LEFT, state.layout().rowAlignment());
        } finally {
            RenderBuffer.clear();
            RenderModule.resetSection();
        }
    }

    @Test void itemAndEntityDrawFunctionsAcceptIdsOrModels() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        RenderModule.register(api, models);
        registry.freeze();

        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@param id ItemId | ItemStack | ItemStackInput Namespaced item ID or ItemStack model"));
        assertTrue(stub.contains("---@param uuid string | Entity | EntityInput Entity UUID or any table with a string uuid field"));
        assertTrue(stub.contains("---@param options? EntityOptions Rendering options"));
        assertTrue(stub.contains("---@field pitch? number Vertical viewing angle offset in degrees"));
        assertTrue(stub.contains("---@field yaw? number Horizontal viewing angle offset in degrees"));
        assertTrue(stub.contains("---@field fullBright? boolean Use maximum lighting; defaults to true"));
        assertTrue(stub.contains("---@field useEntityRotation? boolean "
                + "Use the entity's current rotation as the base for yaw and pitch offsets"));
        assertTrue(stub.contains("function lh.drawEntity(uuid, options) end"));
        assertTrue(stub.contains("---@field rowHeight? integer | \"auto\" Fixed unscaled row height"));
        assertTrue(stub.contains("---@field lineSpacing? integer Unscaled background spacing around each row, favoring the top; defaults to 2"));
        assertTrue(stub.contains("---@field inlineSpacing? integer "
                + "Unscaled background spacing between visible elements; defaults to 0"));
        assertTrue(stub.contains("---@field inlineAlign? \"top\" | \"center\" | \"bottom\" "
                + "Vertical alignment of elements within a row; defaults to center"));
        assertTrue(stub.contains("---@param count? integer Number of line breaks; defaults to 1"));
        assertTrue(stub.contains("function lh.newline(count) end"));
        assertTrue(stub.contains("---@field id ItemId Namespaced item ID"));
        assertTrue(stub.contains("---@field count? integer Stack count; defaults to 1 for input tables"));
        assertTrue(stub.contains("---@field damage? integer Current item damage"));
        assertTrue(stub.contains("---@field damageLeft? integer Remaining durability"));
        assertTrue(stub.contains("---@field enchantments? table<EnchantmentId, integer>"));
        assertTrue(stub.contains("---@field maxDamage? integer Maximum durability"));
        assertTrue(stub.contains("---@field newLine? boolean Advance to the next line after drawing; defaults to false"));
        assertEquals(4, count(stub, "---@field newLine? boolean Advance to the next line after drawing; defaults to false"));
        String entityOptions = stub.substring(stub.indexOf("---@class EntityOptions"));
        entityOptions = entityOptions.substring(0, entityOptions.indexOf("\n\n"));
        assertTrue(entityOptions.contains("---@field newLine? boolean Advance to the next line after drawing; defaults to false"));
    }

    @Test void drawingOptionsRejectMisspelledFields() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        LuaError error = assertThrows(LuaError.class,
                () -> runtime.execute("lh.section({inlineSpcing=2})", "misspelled-options"));
        assertTrue(error.getMessage().contains("unknown field 'inlineSpcing' in SectionOptions"));
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

    @Test void renderOptionsAcceptMutableColorsAndExistingLiterals() throws Exception {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var models = CoreModels.register(api);
        ColorModule.register(api, models.color());
        RenderModule.register(api, models);
        var states = new java.util.ArrayList<SectionState>();
        api.root().function("captureState", dev.ifair.luahud.api.LuaType.nil(), "Capture rendering state")
                .call(args -> { states.add(RenderModule.currentSectionState()); return null; });
        registry.freeze();
        var runtime = new CobaltRuntime(registry);
        runtime.start();
        try {
            runtime.execute("""
                    local color = lh.color.rgb(120, 180, 255)
                    color.a = 128
                    lh.section({textColor = color, backgroundColor = '#112233'})
                    lh.captureState()
                    color.r = 0
                    lh.section({textColor = color, backgroundColor = 0})
                    lh.captureState()
                    lh.canvas({width = 10, height = 10, textColor = color})
                    lh.drawRect(0, 0, 10, 10, {color = color, borderColor = '#FFFFFF'})
                    lh.endCanvas()
                    lh.section({textColor = {r = 1, g = 2, b = 3}})
                    lh.captureState()
                    """, "color-render");
            assertEquals(0x8078B4FF, states.get(0).textColor());
            assertEquals(0xFF112233, states.get(0).backgroundColor());
            assertEquals(0x8000B4FF, states.get(1).textColor());
            assertEquals(0, states.get(1).backgroundColor());
            assertEquals(0xFF010203, states.get(2).textColor());
            assertThrows(LuaError.class, () -> runtime.execute(
                    "lh.section({textColor = {r = 256, g = 0, b = 0}})", "invalid-color-table"));
        } finally {
            RenderBuffer.clear();
            RenderModule.resetSection();
        }
    }

    @Test void numericColorsAreStrictArgb() {
        assertEquals(0x00000000, RenderModule.color(0x0));
        assertEquals(0xFF00FF00, RenderModule.color(0xFF00FF00));
    }

    @Test void sixDigitStringsRemainOpaqueRgb() {
        assertEquals(0xFF00FF00, RenderModule.color("#00FF00"));
        assertEquals(0x00000000, RenderModule.color("#00000000"));
    }

    @Test void sectionsDefaultToZeroHorizontalOffsetAndTwoVerticalOffsetForEveryAnchor() {
        SectionState defaults = RenderModule.sectionState(null);
        assertEquals(0, defaults.layout().offsetX());
        assertEquals(2, defaults.layout().offsetY());

        for (String anchor : List.of("topLeft", "topCenter", "topRight",
                "bottomLeft", "bottomCenter", "bottomRight")) {
            SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of("anchor", anchor)));
            assertEquals(0, state.layout().offsetX(), anchor);
            assertEquals(2, state.layout().offsetY(), anchor);
        }
    }

    @Test void centerAnchorsDefaultToZeroHorizontalOffsetAndCenteredRows() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of("anchor", "topCenter")));

        assertEquals(Anchor.TOP_CENTER, state.layout().anchor());
        assertEquals(0, state.layout().offsetX());
        assertEquals(RowAlignment.CENTER, state.layout().rowAlignment());
        assertEquals(InlineAlignment.CENTER, state.layout().inlineAlignment());
        assertEquals(RowDirection.TOP_TO_BOTTOM, state.layout().rowDirection());
        assertTrue(state.layout().autoRowHeight());
        assertEquals(2, state.layout().lineSpacing());
        assertEquals(0, state.layout().inlineSpacing());
        assertEquals(0, state.layout().scaledInlineSpacing());
    }

    @Test void rightAnchorsDefaultToZeroHorizontalOffsetAndDeriveRightRowAlignment() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of("anchor", "bottomRight")));

        assertEquals(0, state.layout().offsetX());
        assertEquals(2, state.layout().offsetY());
        assertEquals(RowAlignment.RIGHT, state.layout().rowAlignment());
    }

    @Test void sectionDirectionsAndAlignmentCanBeOverridden() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of(
                "anchor", "bottomCenter",
                "rowAlign", "left",
                "inlineAlign", "bottom",
                "inlineDirection", "rightToLeft",
                "rowDirection", "bottomToTop"
        )));

        assertEquals(RowAlignment.LEFT, state.layout().rowAlignment());
        assertEquals(InlineAlignment.BOTTOM, state.layout().inlineAlignment());
        assertEquals(InlineDirection.RIGHT_TO_LEFT, state.layout().inlineDirection());
        assertEquals(RowDirection.BOTTOM_TO_TOP, state.layout().rowDirection());
    }

    @Test void sectionScaleMustBePositiveAndFinite() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("scale", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("scale", Double.POSITIVE_INFINITY))));
    }

    @Test void sectionScaleIconScaleRowHeightAndSpacingAreIndependentOptions() {
        SectionState state = RenderModule.sectionState(new LuaTableValue(Map.of(
                "scale", 2,
                "iconScale", 1.5,
                "rowHeight", 10,
                "lineSpacing", 3,
                "inlineSpacing", 2
        )));

        assertEquals(2f, state.layout().scale());
        assertEquals(1.5f, state.iconScale());
        assertEquals(10, state.layout().rowHeight());
        assertFalse(state.layout().autoRowHeight());
        assertEquals(20, state.layout().scaledRowHeight());
        assertEquals(3, state.layout().lineSpacing());
        assertEquals(4, state.layout().spacingAbove());
        assertEquals(2, state.layout().spacingBelow());
        assertEquals(6, state.layout().scaledLineSpacing());
        assertEquals(2, state.layout().inlineSpacing());
        assertEquals(4, state.layout().scaledInlineSpacing());
    }

    @Test void inlineSpacingDefaultsToZeroAndCannotBeNegative() {
        assertEquals(0, RenderModule.sectionState(null).layout().inlineSpacing());
        assertEquals(0, RenderModule.sectionState(new LuaTableValue(Map.of())).layout().inlineSpacing());
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("inlineSpacing", -1))));
    }

    @Test void automaticRowHeightIsTheDefaultAndCanBeRequestedExplicitly() {
        SectionState defaults = RenderModule.sectionState(null);
        SectionState explicit = RenderModule.sectionState(new LuaTableValue(Map.of("rowHeight", "auto")));

        assertTrue(defaults.layout().autoRowHeight());
        assertTrue(explicit.layout().autoRowHeight());
        assertEquals(12, explicit.layout().rowHeight());
    }

    @Test void iconScaleAndRowHeightMustBePositiveAndLineSpacingCannotBeNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("iconScale", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("rowHeight", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> RenderModule.sectionState(new LuaTableValue(Map.of("lineSpacing", -1))));
    }
}
