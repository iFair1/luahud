package dev.ifair.luahud.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private JsonObject document;

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
        document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
    }

    @ParameterizedTest
    @CsvSource({"reload, nil", "tick, nil", "render, nil", "item-pickup, ItemStack",
            "damage, Damage", "death, Death", "error, Error"})
    void builtInEventsHaveMatchingRegistryDocumentationAndLuaOverloads(String name, String payload) {
        var event = registry.event(name);
        JsonObject exported = find(document.getAsJsonArray("events"), name);
        boolean hasPayload = !payload.equals("nil");

        assertEquals("luahud", event.providerId());
        assertEquals(hasPayload, event.hasPayload());
        assertEquals(hasPayload, exported.get("hasPayload").getAsBoolean());
        assertEquals(payload, exported.getAsJsonObject("payloadType").get("display").getAsString());
        if (hasPayload) {
            assertEquals("model", find(document.getAsJsonArray("types"), payload).get("kind").getAsString());
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
        JsonObject definition = find(document.getAsJsonArray("types"), model);
        JsonObject field = find(definition.getAsJsonArray("fields"), name);

        assertEquals(type, field.getAsJsonObject("type").get("display").getAsString());
        assertEquals(optional, field.get("optional").getAsBoolean());
    }

    private static JsonObject find(JsonArray values, String name) {
        for (var value : values) {
            JsonObject item = value.getAsJsonObject();
            if (item.get("name").getAsString().equals(name)) return item;
        }
        throw new AssertionError("Missing API definition: " + name);
    }
}
