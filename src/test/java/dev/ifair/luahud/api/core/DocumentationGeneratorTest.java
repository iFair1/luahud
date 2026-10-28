package dev.ifair.luahud.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ifair.luahud.api.LuaDoc;
import dev.ifair.luahud.api.LuaType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentationGeneratorTest {
    @Test void exportsTreeAndOrderedParametersWithoutRunningBindings() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        String description = "Quoted \"text\"\n<example> & Türkçe";
        api.root().namespace("player", ns -> ns.string("name", description, () -> {
            throw new AssertionError("Documentation must not evaluate suppliers");
        }));
        var function = api.root().function("example", LuaType.optional(LuaType.string()), description);
        function.parameter("z", LuaType.integer(), "First");
        function.parameter("a", LuaType.optional(LuaType.string()), "Second");
        function.call(args -> { throw new AssertionError("Must not invoke functions"); });
        api.event("reload", "");
        api.event("tick", "");
        api.event("render", "");
        registry.addEventFunctions();
        registry.freeze();

        String json = DocumentationGenerator.generate(registry);
        assertEquals(json, DocumentationGenerator.generate(registry));
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(Set.of("schemaVersion", "root", "types", "events"), document.keySet());
        assertEquals(3, document.get("schemaVersion").getAsInt());
        JsonObject root = document.getAsJsonObject("root");
        assertEquals("lh", root.get("name").getAsString());
        validateTree(root, "lh", new HashSet<>());
        JsonObject property = find(find(root, "player"), "name");
        assertEquals("lh.player.name", property.get("path").getAsString());
        assertEquals("property", property.get("kind").getAsString());
        assertEquals(description, property.get("description").getAsString());
        JsonObject example = find(root, "example");
        assertEquals("function", example.get("kind").getAsString());
        JsonArray parameters = example.getAsJsonArray("parameters");
        assertEquals("z", parameters.get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(parameters.get(0).getAsJsonObject().get("optional").getAsBoolean());
        assertEquals("a", parameters.get(1).getAsJsonObject().get("name").getAsString());
        assertTrue(parameters.get(1).getAsJsonObject().get("optional").getAsBoolean());
        assertJson("{kind:'primitive',name:'string'}", parameters.get(1).getAsJsonObject().getAsJsonObject("type"));
        assertJson("{kind:'optional',element:{kind:'primitive',name:'string'}}", example.getAsJsonObject("returnType"));
        assertJson("{kind:'primitive',name:'string'}", find(root, "on").getAsJsonArray("parameters")
                .get(0).getAsJsonObject().getAsJsonObject("type"));
    }

    @Test void namedReferencesOmitProviderPrefixesAndDoNotExpandRecursiveDefinitions() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("example-addon");
        var model = api.model("Value", String.class);
        var text = model.string("text", "Required input", value -> value).inputRequired();
        model.optionalField("next", model.type(), "Recursive output", value -> null);
        model.fromFields(fields -> fields.get(text));
        var table = LuaType.inputTable("Options", (fields, self) -> fields.optional("next", self, "Recursive input"));
        var function = api.root().function("echo", model.type(), "Echo");
        function.parameter("options", LuaType.optional(table), "Options");
        function.parameter("values", LuaType.list(model.inputType()), "Values");
        function.call(args -> null);
        registry.freeze();

        JsonObject echo = find(root(registry), "echo");
        assertJson("{kind:'reference',id:'Value'}", echo.getAsJsonObject("returnType"));
        JsonArray parameters = echo.getAsJsonArray("parameters");
        assertJson("{kind:'reference',id:'Options'}", parameters.get(0).getAsJsonObject().getAsJsonObject("type"));
        assertJson("{kind:'list',element:{kind:'union',alternatives:[{kind:'reference',id:'Value'},"
                + "{kind:'reference',id:'ValueInput'}]}}", parameters.get(1).getAsJsonObject().getAsJsonObject("type"));
    }

    @Test void exportsCompoundTypesAndAliasesWithoutReadingAliasValues() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("addon");
        var id = LuaType.stringAlias("ItemId", () -> {
            throw new AssertionError("Documentation must not evaluate alias suppliers");
        });
        var value = LuaType.optional(LuaType.list(LuaType.union(LuaType.bool(), LuaType.literal("quoted \"value\""))));
        api.root().value("items", LuaType.map(id, value), LuaDoc.NONE, () -> {
            throw new AssertionError("Must not evaluate suppliers");
        });
        var opaque = api.model("Opaque", String.class);
        opaque.string("value", "", text -> text);
        var function = api.root().function("echo", LuaType.nil(), "");
        function.parameter("value", opaque.inputType(), "");
        function.call(args -> null);
        registry.freeze();

        JsonObject root = root(registry);
        JsonObject type = find(root, "items").getAsJsonObject("type");
        assertEquals("map", type.get("kind").getAsString());
        assertJson("{kind:'reference',id:'ItemId'}", type.getAsJsonObject("key"));
        assertJson("{kind:'optional',element:{kind:'list',element:{kind:'union',alternatives:"
                + "[{kind:'primitive',name:'boolean'},{kind:'literal',value:'quoted \"value\"'}]}}}", type.getAsJsonObject("value"));
        assertJson("{kind:'reference',id:'Opaque'}", find(root, "echo").getAsJsonArray("parameters")
                .get(0).getAsJsonObject().getAsJsonObject("type"));
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
        assertTrue(root(withoutAddon).getAsJsonArray("children").isEmpty());
    }

    @Test void exportsSeparateOutputAndRecursiveInputDefinitions() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var model = api.model("Value", String.class);
        model.string("text", "Required text", value -> {
            throw new AssertionError("Must not evaluate model accessors");
        }).inputRequired();
        model.integer("count", "Count", value -> 1).inputDefault(1);
        model.optionalField("next", LuaType.list(model.type()), "Nested values", value -> null).inputOptional();
        model.string("output", "Output only", value -> value);
        model.fromFields(fields -> { throw new AssertionError("Must not decode models"); });
        var options = LuaType.inputTable("Options", (fields, self) -> fields
                .field("value", model.inputType(), "Value")
                .optional("next", self, "Nested options"));
        var fn = api.root().function("use", LuaType.nil(), "");
        fn.parameter("options", options, "");
        fn.call(args -> null);
        registry.freeze();

        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonArray types = document.getAsJsonArray("types");
        JsonObject output = definition(types, "Value");
        assertEquals("model", output.get("kind").getAsString());
        assertEquals(4, output.getAsJsonArray("fields").size());
        assertJson("{name:'next',description:'Nested values',optional:true,type:{kind:'list',element:{kind:'reference',id:'Value'}}}",
                fieldNamed(output, "next"));
        JsonObject input = definition(types, "ValueInput");
        assertEquals("input", input.get("kind").getAsString());
        assertJson("{kind:'reference',id:'Value'}", input.getAsJsonObject("model"));
        assertEquals(3, input.getAsJsonArray("fields").size());
        assertFalse(fieldNamed(input, "text").get("optional").getAsBoolean());
        assertTrue(fieldNamed(input, "count").get("optional").getAsBoolean());
        assertJson("{kind:'list',element:{kind:'union',alternatives:[{kind:'reference',id:'Value'},{kind:'reference',id:'ValueInput'}]}}",
                fieldNamed(input, "next").getAsJsonObject("type"));
        assertJson("{kind:'reference',id:'Options'}", fieldNamed(definition(types, "Options"), "next").getAsJsonObject("type"));
        assertReferencesResolve(document, types);
    }

    @Test void exportsAliasDescriptionsAndEventPayloadsWithoutRegistrySnapshots() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var id = LuaType.stringAlias("ItemId", "Namespaced item registry ID", () -> {
            throw new AssertionError("Must not read registry IDs");
        });
        var payload = api.model("Pickup", String.class);
        payload.field("id", id, "Item ID", value -> value);
        api.event("pickup", payload.type(), "Picked up an item");
        api.event("reload", "Reloaded");
        var alias = LuaType.alias("Choice", LuaType.union(LuaType.literal("one"), LuaType.literal("two")));
        api.root().value("choice", alias, LuaDoc.NONE, () -> "one");
        registry.freeze();

        JsonObject document = JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        JsonArray types = document.getAsJsonArray("types");
        assertJson("{id:'ItemId',name:'ItemId',kind:'alias',description:'Namespaced item registry ID',type:{kind:'primitive',name:'string'}}",
                definition(types, "ItemId"));
        assertEquals("union", definition(types, "Choice").getAsJsonObject("type").get("kind").getAsString());
        assertJson("{name:'pickup',description:'Picked up an item',payloadType:{kind:'reference',id:'Pickup'}}",
                document.getAsJsonArray("events").get(0).getAsJsonObject());
        assertJson("{name:'reload',description:'Reloaded',payloadType:{kind:'primitive',name:'nil'}}",
                document.getAsJsonArray("events").get(1).getAsJsonObject());
        assertReferencesResolve(document, types);
    }

    @Test void disambiguatesTypeNamesIncludingGeneratedInputNames() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var reserved = api.model("ValueInput", String.class);
        var value = api.model("Value", String.class);
        var text = value.string("text", "", v -> v).inputRequired();
        value.fromFields(fields -> fields.get(text));
        registry.registerAddon("addon", addon -> {
            var other = addon.model("Value", Integer.class);
            addon.event("other", other.type(), "");
        });
        api.event("input", value.type(), "");
        var fn = api.root().function("use", reserved.type(), "");
        fn.parameter("value", value.inputType(), "");
        fn.call(args -> null);
        registry.freeze();

        String json = DocumentationGenerator.generate(registry);
        assertEquals(json, DocumentationGenerator.generate(registry));
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        JsonArray types = document.getAsJsonArray("types");
        assertEquals(4, types.size());
        assertEquals("model", definition(types, "ValueInput").get("kind").getAsString());
        assertEquals("input", definition(types, "ValueInput_2").get("kind").getAsString());
        assertReferencesResolve(document, types);
    }

    private static JsonObject definition(JsonArray types, String id) {
        for (var entry : types) {
            if (entry.getAsJsonObject().get("id").getAsString().equals(id)) return entry.getAsJsonObject();
        }
        throw new AssertionError("Missing definition " + id);
    }

    private static JsonObject fieldNamed(JsonObject definition, String name) {
        for (var entry : definition.getAsJsonArray("fields")) {
            if (entry.getAsJsonObject().get("name").getAsString().equals(name)) return entry.getAsJsonObject();
        }
        throw new AssertionError("Missing field " + name);
    }

    private static void assertReferencesResolve(JsonObject document, JsonArray types) {
        Set<String> ids = new HashSet<>();
        for (var entry : types) assertTrue(ids.add(entry.getAsJsonObject().get("id").getAsString()));
        assertReferencesResolve(document, ids);
    }

    private static void assertReferencesResolve(com.google.gson.JsonElement element, Set<String> ids) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> assertReferencesResolve(child, ids));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("kind") && object.get("kind").getAsString().equals("reference")) {
                assertTrue(ids.contains(object.get("id").getAsString()), "Unresolved reference: " + object);
            }
            object.entrySet().forEach(entry -> assertReferencesResolve(entry.getValue(), ids));
        }
    }

    private static JsonObject root(ApiRegistry registry) {
        return JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject().getAsJsonObject("root");
    }

    private static void assertJson(String expected, JsonObject actual) {
        assertEquals(JsonParser.parseString(expected), actual);
    }

    private static void validateTree(JsonObject node, String path, Set<String> paths) {
        assertEquals(path, node.get("path").getAsString());
        assertTrue(paths.add(path));
        assertTrue(node.has("description"));
        if (!node.get("kind").getAsString().equals("namespace")) return;
        String previous = "";
        for (var child : node.getAsJsonArray("children")) {
            JsonObject item = child.getAsJsonObject();
            String name = item.get("name").getAsString();
            assertTrue(previous.compareTo(name) < 0);
            previous = name;
            validateTree(item, path + "." + name, paths);
        }
    }

    private static JsonObject find(JsonObject namespace, String name) {
        for (var value : namespace.getAsJsonArray("children")) {
            JsonObject item = value.getAsJsonObject();
            if (item.get("name").getAsString().equals(name)) return item;
        }
        throw new AssertionError("Missing " + name);
    }
}
