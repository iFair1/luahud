package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaType;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @CsvSource({"reload, nil", "tick, nil", "render, nil", "item-pickup, ItemStack",
            "damage, Damage", "death, Death", "error, Error"})
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
