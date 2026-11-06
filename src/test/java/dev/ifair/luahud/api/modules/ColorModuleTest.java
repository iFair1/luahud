package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.core.StubGenerator;
import dev.ifair.luahud.api.models.ColorModel;
import dev.ifair.luahud.core.CobaltRuntime;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ColorModuleTest {
    @Test void constructorsPreserveAlphaAndArgbRoundTrips() throws Exception {
        var runtime = runtime();
        runtime.execute("""
                local rgb = lh.color.rgb(120, 180, 255)
                assert(type(rgb) == 'table')
                assert(rgb.r == 120 and rgb.g == 180 and rgb.b == 255 and rgb.a == 255)
                assert(rgb.asString == '#FF78B4FF')
                assert(lh.color.rgb(120, 180, 255, 128).asString == '#8078B4FF')
                assert(lh.color.from('#78B4FF').asString == '#FF78B4FF')
                assert(lh.color.from('8078b4ff').asString == '#8078B4FF')
                assert(lh.color.from(0x8078B4FF).asString == '#8078B4FF')
                assert(lh.color.from(0xFF78B4FF).asString == '#FF78B4FF')
                assert(lh.color.from(0).asString == '#00000000')
                assert(lh.color.from(0x112233).asString == '#00112233')
                assert(lh.color.fromRgb(0x112233).asString == '#FF112233')
                assert(lh.color.fromRgb(0x80112233).asString == '#FF112233')
                for _, hex in ipairs({'00000000', '00112233', '8078B4FF', 'FFFFFFFF'}) do
                    local color = lh.color.from(hex)
                    assert(lh.color.from(color.asInt).asString == '#' .. hex)
                    assert(lh.color.from(color.asString).asInt == color.asInt)
                end
                """, "color-constructors");
    }

    @Test void mutationsUpdateConversionsAndTablesAreIndependentSnapshots() throws Exception {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var model = ColorModel.register(api);
        ColorModule.register(api, model);
        var source = new AtomicInteger(0x78B4FF);
        api.root().model("sky", model, "Test biome color", () -> ColorModel.fromRgb(source.get()));
        registry.freeze();
        var runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("saved = lh.sky; saved.a = 128; saved.r = 0", "color-mutation");
        source.set(0x112233);
        runtime.execute("""
                assert(saved.asString == '#8000B4FF')
                assert(saved.asInt == lh.color.from('#8000B4FF').asInt)
                assert(lh.sky.asString == '#FF112233')
                local count = 0
                for k, v in pairs(saved) do count = count + 1 end
                assert(count == 4)
                local other = lh.color.rgb(0, 0, 0)
                other.a = 0
                assert(other.asString == '#00000000' and saved.a == 128)
                """, "color-snapshot");
    }

    @Test void invalidColorsFailInsteadOfSilentlyWrapping() throws Exception {
        var runtime = runtime();
        for (String expression : new String[] {
                "lh.color.rgb(-1, 0, 0)", "lh.color.rgb(0, 256, 0)", "lh.color.rgb(0, 0, 0, 256)",
                "lh.color.from('#123')", "lh.color.from('#GG112233')", "lh.color.from('#123456789')",
                "local c = lh.color.rgb(0, 0, 0); c.r = 256; result = c.asInt",
                "local c = lh.color.rgb(0, 0, 0); c.r = nil; result = c.asString"
        }) {
            assertThrows(LuaError.class, () -> runtime.execute(expression, "invalid-color"), expression);
        }
    }

    @Test void stubsDocumentChannelsAndConstructorSignatures() {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        ColorModule.register(api, ColorModel.register(api));
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@class Color"));
        assertTrue(stub.contains("---@field asInt integer"));
        assertTrue(stub.contains("---@field asString string"));
        assertTrue(stub.contains("function lh.color.rgb(r, g, b, a) end"));
        assertTrue(stub.contains("function lh.color.from(value) end"));
        assertTrue(stub.contains("function lh.color.fromRgb(value) end"));
    }

    private CobaltRuntime runtime() throws Exception {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        ColorModule.register(api, ColorModel.register(api));
        registry.freeze();
        var runtime = new CobaltRuntime(registry);
        runtime.start();
        return runtime;
    }
}
