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
        assertEquals(Set.of("schemaVersion", "root"), document.keySet());
        assertEquals(2, document.get("schemaVersion").getAsInt());
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
