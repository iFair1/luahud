package dev.ifair.luahud.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ifair.luahud.api.LuaType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentationGeneratorTest {
    @Test void builtInDeathEventExportsItsModelAndCallbackType() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        CoreApi.register(registry, null);
        registry.freeze();

        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@class Death\n"));
        assertTrue(stub.contains("---@field x number"));
        assertTrue(stub.contains("---@field y number"));
        assertTrue(stub.contains("---@field z number"));
        assertTrue(stub.contains("---@field dimension string"));
        assertTrue(stub.contains("---@field attacker? Entity"));
        assertTrue(stub.contains("---@field weapon? ItemStack"));
        assertTrue(stub.contains("---@overload fun(event: \"death\", callback: fun(event: Death))"));
        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonObject event = find(document.getAsJsonArray("events"), "name", "death");
        assertTrue(event.get("hasPayload").getAsBoolean());
        assertNotNull(find(document.getAsJsonArray("types"), "name", "Death"));
    }

    @Test void builtInErrorEventExportsItsModelAndCallbackType() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        CoreApi.register(registry, null);
        registry.freeze();

        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@class Error\n"));
        assertTrue(stub.contains("---@field line integer"));
        assertTrue(stub.contains("---@field message string"));
        assertTrue(stub.contains("---@field isNew boolean"));
        assertTrue(stub.contains("---@field count integer"));
        assertTrue(stub.contains("---@overload fun(event: \"error\", callback: fun(event: Error))"));
        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonObject event = find(document.getAsJsonArray("events"), "name", "error");
        assertTrue(event.get("hasPayload").getAsBoolean());
        assertNotNull(find(document.getAsJsonArray("types"), "name", "Error"));
    }

    @Test void exportsDescriptionsAndOrderedParametersWithoutRunningBindings() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        String description = "Quoted \"text\"\n<example> & Türkçe";
        api.root().namespace("player", ns -> ns.string("name", description, () -> {
            throw new AssertionError("Documentation must not evaluate suppliers");
        }));
        var function = api.root().function("example", LuaType.string(), description);
        function.parameter("z", LuaType.integer(), "First");
        function.parameter("a", LuaType.optional(LuaType.string()), "Second");
        function.call(args -> { throw new AssertionError("Must not invoke functions"); });
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.freeze();

        String json = DocumentationGenerator.generate(registry);
        assertEquals(json, DocumentationGenerator.generate(registry));
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, document.get("schemaVersion").getAsInt());
        JsonObject property = document.getAsJsonArray("properties").get(0).getAsJsonObject();
        assertEquals("lh.player.name", property.get("path").getAsString());
        assertEquals(description, property.get("description").getAsString());
        JsonObject example = find(document.getAsJsonArray("functions"), "path", "lh.example");
        JsonArray parameters = example.getAsJsonArray("parameters");
        assertEquals("z", parameters.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("a", parameters.get(1).getAsJsonObject().get("name").getAsString());
        assertTrue(parameters.get(1).getAsJsonObject().get("optional").getAsBoolean());
        JsonObject on = find(document.getAsJsonArray("functions"), "path", "lh.on");
        assertEquals("string", on.getAsJsonArray("parameters").get(0).getAsJsonObject()
                .getAsJsonObject("type").get("kind").getAsString());
    }

    @Test void recursiveModelsAndInputTablesUseResolvableStubNames() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("example-addon");
        var model = api.model("Value", String.class);
        var text = model.string("text", "Required input", value -> value).inputRequired();
        model.optionalField("next", model.type(), "Recursive output", value -> null);
        model.integer("count", "Default input", value -> 1).inputDefault(1);
        model.fromFields(fields -> fields.get(text));
        var table = LuaType.inputTable("Options", fields -> fields.field("values", LuaType.list(model.inputType()), "Values"));
        var function = api.root().function("echo", model.type(), "Echo");
        function.parameter("options", table, "Options");
        function.call(args -> null);
        registry.freeze();

        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonArray types = document.getAsJsonArray("types");
        String stub = StubGenerator.generate(registry);
        Set<String> names = new HashSet<>();
        types.forEach(value -> {
            String name = value.getAsJsonObject().get("name").getAsString();
            assertTrue(names.add(name));
            assertTrue(stub.contains("---@class " + name + "\n"));
        });
        JsonObject input = find(types, "name", "example_addon_ValueInput");
        assertEquals(2, input.getAsJsonArray("fields").size());
        assertFalse(find(input.getAsJsonArray("fields"), "name", "text").get("optional").getAsBoolean());
        assertEquals("default", find(input.getAsJsonArray("fields"), "name", "count").get("inputMode").getAsString());
        JsonObject options = find(types, "name", "example_addon_Options");
        JsonObject list = options.getAsJsonArray("fields").get(0).getAsJsonObject().getAsJsonObject("type");
        assertEquals("(example_addon_Value | example_addon_ValueInput)[]", list.get("display").getAsString());
        list.getAsJsonObject("elementType").getAsJsonArray("references").forEach(ref -> assertTrue(names.contains(ref.getAsString())));
    }

    @Test void requiresFrozenDefinitionsAndDropsRemovedAddons() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        assertThrows(IllegalArgumentException.class, () -> DocumentationGenerator.generate(registry));
        registry.registerAddon("addon", api -> api.root().integer("addonCount", "", () -> 1));
        registry.freeze();
        assertTrue(DocumentationGenerator.generate(registry).contains("lh.addonCount"));
        ApiRegistry withoutAddon = new ApiRegistry(ignored -> {});
        withoutAddon.freeze();
        assertFalse(DocumentationGenerator.generate(withoutAddon).contains("lh.addonCount"));
    }

    @Test void eventMetadataIncludesProviderAndResolvablePayloadTypes() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("addon");
        var model = api.model("Damage", String.class);
        model.string("attacker", "", value -> value);
        api.event("damage", model.type(), "Damage details");
        api.event("reload", "Reloaded");
        registry.addEventFunctions();
        registry.freeze();
        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonObject damage = find(document.getAsJsonArray("events"), "name", "damage");
        assertEquals("addon", damage.get("providerId").getAsString());
        assertTrue(damage.get("hasPayload").getAsBoolean());
        assertEquals("addon_Damage", damage.getAsJsonObject("payloadType").get("display").getAsString());
        assertFalse(find(document.getAsJsonArray("events"), "name", "reload").get("hasPayload").getAsBoolean());
    }

    private static JsonObject find(JsonArray values, String key, String expected) {
        for (var value : values) {
            JsonObject item = value.getAsJsonObject();
            if (item.get(key).getAsString().equals(expected)) return item;
        }
        throw new AssertionError("Missing " + expected);
    }
}
