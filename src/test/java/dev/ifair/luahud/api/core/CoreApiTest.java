package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.core.CobaltRuntime;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.squiddev.cobalt.LuaError;

class CoreApiTest {
    private ApiRegistry registry;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @BeforeEach
    void registerBuiltInApiWithoutAClient() {
        registry = new ApiRegistry(message -> { throw new AssertionError(message); });
        CoreApi.register(registry, null);
        registry.freeze();
    }

    @Test
    void getTranslationUsesTheCurrentLanguageOnEveryCallAndFallsBackToTheKey() throws Exception {
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        Language original = Language.getInstance();
        try {
            Language.setInstance(language("Hello"));
            runtime.execute("first = lh.getTranslation('luahud.test.greeting')", "translation");
            assertEquals("Hello", runtime.global("first").checkString());

            Language.setInstance(language("Merhaba"));
            runtime.execute("second = lh.getTranslation('luahud.test.greeting'); missing = lh.getTranslation('luahud.test.missing')", "translation");
            assertEquals("Merhaba", runtime.global("second").checkString());
            assertEquals("luahud.test.missing", runtime.global("missing").checkString());
        } finally {
            Language.setInstance(original);
        }
    }

    @Test
    void getTranslationRequiresAKeyAndExposesItsSignatureInStubs() throws Exception {
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        assertThrows(LuaError.class, () -> runtime.execute("lh.getTranslation()", "missing-key"));
        assertThrows(LuaError.class, () -> runtime.execute("lh.getTranslation({})", "invalid-key"));
        assertTrue(StubGenerator.generate(registry).contains("function lh.getTranslation(key) end"));
    }

    private static Language language(String greeting) {
        return new Language() {
            @Override public String get(String key, String fallback) {
                return hasTranslation(key) ? greeting : fallback;
            }
            @Override public boolean hasTranslation(String key) {
                return key.equals("luahud.test.greeting");
            }
            @Override public boolean isRightToLeft() { return false; }
            @Override public OrderedText reorder(StringVisitable text) { return OrderedText.EMPTY; }
        };
    }

    @ParameterizedTest
    @CsvSource({"reload, nil", "tick, nil", "render, nil", "item-pickup, ItemStack",
            "damage, Damage", "attack, Damage", "death, Death", "kill, Death", "error, Error"})
    void builtInEventsHaveMatchingRegistryAndLuaOverloads(String name, String payload) {
        var event = registry.event(name);
        boolean hasPayload = !payload.equals("nil");

        assertEquals("luahud", event.providerId());
        assertEquals(hasPayload, event.hasPayload());
        assertEquals(payload, event.payloadType().displayName());
        if (hasPayload) {
            assertEquals(LuaType.Kind.MODEL, event.payloadType().kind());
            assertTrue(registry.models().contains(event.payloadType().model()));
        }
        String callback = hasPayload ? "fun(event: " + payload + ")" : "fun()";
        assertTrue(StubGenerator.generate(registry).contains(
                "---@overload fun(event: \"" + name + "\", callback: " + callback + ")"));
    }

    @ParameterizedTest
    @CsvSource({
            "Death, x, number, false", "Death, y, number, false", "Death, z, number, false",
            "Death, dimension, string, false", "Death, message, string, false",
            "Damage, victim, Entity, true", "Death, victim, Entity, true",
            "Death, damageType, string, false", "Death, attacker, Entity, true", "Death, weapon, ItemStack, true",
            "Error, id, number, false", "Error, group, string, false", "Error, system, boolean, false",
            "Error, phase, string, false", "Error, line, integer, false", "Error, message, string, false",
            "Error, count, integer, false", "Error, isNew, boolean, false"
    })
    void eventFieldsBelongToTheExpectedModel(String model, String name, String type, boolean optional) {
        var definition = registry.models().stream().filter(value -> value.name().equals(model)).findFirst().orElseThrow();
        var field = definition.fields().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();

        assertEquals(type, field.type().displayName());
        assertEquals(optional, field.optional());
    }
}
