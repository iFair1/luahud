package dev.ifair.luahud.api.core;

import com.google.gson.JsonParser;
import dev.ifair.luahud.api.modules.OptionsModule;
import dev.ifair.luahud.core.CobaltRuntime;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.option.GameOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.squiddev.cobalt.LuaError;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OptionsModuleTest {
    @TempDir Path temp;

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void readsCurrentSettingsWithNativeLuaTypesAndDoesNotWriteOptions() throws Exception {
        GameOptions options = defaultOptions();
        AtomicReference<GameOptions> current = new AtomicReference<>(options);
        ApiRegistry registry = registry(current);
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("""
                fov = lh.getOption('fov')
                sensitivity = lh.getOption('mouseSensitivity')
                vsync = lh.getOption('enableVsync')
                language = lh.getOption('language')
                perspective = lh.getOption('perspective')
                volume = lh.getOption('soundCategory_master')
                """, "options");
        assertEquals(options.getFov().getValue().intValue(), runtime.global("fov").checkInteger());
        assertEquals(options.getMouseSensitivity().getValue(), runtime.global("sensitivity").checkDouble());
        assertEquals(options.getEnableVsync().getValue(), runtime.global("vsync").checkBoolean());
        assertEquals(options.language, runtime.global("language").checkString());
        assertEquals("first_person", runtime.global("perspective").checkString());
        assertEquals(1.0, runtime.global("volume").checkDouble());

        options.hudHidden = true;
        runtime.execute("hidden = lh.getOption('hudHidden')", "changed-option");
        assertTrue(runtime.global("hidden").checkBoolean());
        GameOptions replacement = defaultOptions();
        replacement.language = "tr_tr";
        current.set(replacement);
        runtime.execute("language = lh.getOption('language')", "replaced-options");
        assertEquals("tr_tr", runtime.global("language").checkString());
        assertFalse(java.nio.file.Files.exists(temp.resolve("options.txt")));

        var function = (ApiRegistry.FunctionNode) registry.rootNode().children().get("getOption");
        for (var overload : function.overloads()) {
            String name = overload.parameterTypes().getFirst().displayName();
            runtime.execute("value = lh.getOption('" + name + "')", "catalogue");
            assertFalse(runtime.global("value").isNil(), name);
        }
    }

    @Test void rejectsMissingInvalidUnknownAndUnavailableOptions() throws Exception {
        ApiRegistry registry = registry(new AtomicReference<>());
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        assertThrows(LuaError.class, () -> runtime.execute("lh.getOption()", "missing"));
        assertThrows(LuaError.class, () -> runtime.execute("lh.getOption({})", "invalid"));
        assertTrue(assertThrows(LuaError.class, () -> runtime.execute("lh.getOption('unknown')", "unknown"))
                .getMessage().contains("Unknown Minecraft option: unknown"));
        assertTrue(assertThrows(LuaError.class, () -> runtime.execute("lh.getOption('fov')", "unavailable"))
                .getMessage().contains("Minecraft client options are unavailable"));
    }

    @Test void catalogueDrivesCompletionsTypedOverloadsAndDocumentationWithoutAClient() {
        ApiRegistry registry = registry(new AtomicReference<>());
        String stub = StubGenerator.generate(registry);
        assertFalse(stub.contains("---@alias OptionId"));
        assertFalse(stub.contains("---@param name string"));
        assertTrue(stub.contains("---@param name \"advancedItemTooltips\""));
        assertTrue(stub.contains("---@overload fun(name: \"renderDistance\"): integer"));
        assertTrue(stub.contains("---@overload fun(name: \"fov\"): integer"));
        assertTrue(stub.contains("---@overload fun(name: \"enableVsync\"): boolean"));
        assertTrue(stub.contains("---@overload fun(name: \"mouseSensitivity\"): number"));
        assertTrue(stub.contains("---@overload fun(name: \"language\"): string"));
        assertTrue(stub.contains("---@overload fun(name: \"perspective\"): \"first_person\" | \"third_person_back\" | \"third_person_front\""));
        assertTrue(StubGenerator.generate(registry, true).contains("---@overload fun(name: \"fov\"): number"));
        var document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        var function = document.getAsJsonObject("root").getAsJsonArray("children").get(0).getAsJsonObject();
        var definition = (ApiRegistry.FunctionNode) registry.rootNode().children().get("getOption");
        assertEquals(definition.overloads().size(), function.getAsJsonArray("overloads").size());
    }

    private static ApiRegistry registry(AtomicReference<GameOptions> options) {
        ApiRegistry registry = new ApiRegistry(message -> fail(message));
        OptionsModule.register(registry.registrar("luahud"), options::get);
        registry.freeze();
        return registry;
    }

    private GameOptions defaultOptions() {
        return new GameOptions(null, temp.toFile()) {
            // Keep default values and avoid Fabric's resource-pack tracking files during tests.
            @Override public void load() {}
        };
    }
}
