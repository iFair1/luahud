package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.FunctionBuilder;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StubGeneratorTest {
    @TempDir Path temp;

    @Test void outputIsDeterministicMergedEscapedAndMatchesRuntimePaths() {
        ApiRegistry registry = fixture();
        String first = StubGenerator.generate(registry), second = StubGenerator.generate(registry);
        assertEquals(first, second);
        assertTrue(first.contains("---@field count integer Current count"));
        assertTrue(first.contains("---@field label string Description @field cannot inject"));
        assertTrue(first.indexOf("---@class API") < first.indexOf("lh = {}"));
        assertFalse(first.contains("---@type API"));
        assertFalse(first.contains("---@class API\n\n"));
        assertFalse(first.contains("\n\nlh = {}"));
        assertTrue(first.contains("function lh.example.echo(text) end"));
        assertTrue(first.indexOf("function lh.example.echo") < first.indexOf("function lh.zed"));
        assertFalse(first.contains("effects"));
    }

    @Test void writesOnlyWhenChangedAndReplacesOldContent() throws Exception {
        Path target = temp.resolve("api/lh.lua"); String content = StubGenerator.generate(fixture());
        assertTrue(StubGenerator.writeIfChanged(target, content));
        long modified = Files.getLastModifiedTime(target).toMillis();
        assertFalse(StubGenerator.writeIfChanged(target, content));
        assertEquals(modified, Files.getLastModifiedTime(target).toMillis());
        assertTrue(StubGenerator.writeIfChanged(target, content + "-- changed\n"));
        assertTrue(Files.readString(target).endsWith("-- changed\n"));
        try (var files = Files.list(target.getParent())) { assertEquals(1, files.count()); }
    }

    @Test void removedAddonDefinitionsDisappear() {
        String merged = StubGenerator.generate(fixture());
        ApiRegistry coreOnly = new ApiRegistry(ignored -> {});
        coreOnly.registrar("luahud").root().integer("count", "", () -> 1);
        coreOnly.freeze();
        assertTrue(merged.contains("lh.zed"));
        assertFalse(StubGenerator.generate(coreOnly).contains("lh.zed"));
    }

    @Test void inputContractsGroupUnionsInsideListsAndOptionalFields() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        var model = api.model("Value", String.class);
        var text = model.string("text", "", value -> value).inputRequired();
        model.fromFields(fields -> fields.get(text));
        var echo = api.root().function("echo", LuaType.optional(model.inputType()), "");
        echo.parameter("values", LuaType.list(model.inputType()), "");
        echo.call(args -> null);
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@param values (fixture_Value | fixture_ValueInput)[]"));
        assertTrue(stub.contains("---@return (fixture_Value | fixture_ValueInput)?"));
    }

    @Test void eventOverloadsDescribeAddonModelsAndNoArgumentCallbacks() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("example-addon");
        var model = api.model("Damage", String.class);
        model.string("attacker", "", value -> value);
        api.event("damage", model.type(), "Damage payload");
        api.event("reload", "");
        registry.addEventFunctions();
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@overload fun(event: \"damage\", callback: fun(event: example_addon_Damage))"));
        assertTrue(stub.contains("---@overload fun(event: \"reload\", callback: fun())"));
        assertTrue(stub.contains("---@param event string Registered event name"));
        assertFalse(stub.contains("---@param event \""));
    }

    @Test void aliasesReadFreshSortedValuesOnlyDuringStubGeneration() {
        var values = new java.util.ArrayList<String>();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        LuaType<String> id = LuaType.stringAlias("ItemId", () -> {
            reads.incrementAndGet();
            return values;
        });
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.root().value("id", id, LuaDoc.NONE, () -> "minecraft:stone");
        api.root().value("count", LuaType.alias("Count", LuaType.integer()), LuaDoc.NONE, () -> 1);
        registry.freeze();
        DocumentationGenerator.generate(registry);
        assertEquals(0, reads.get());
        values.addAll(java.util.List.of("minecraft:stone", "minecraft:air", "minecraft:stone"));
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@alias ItemId\n---| \"minecraft:air\"\n---| \"minecraft:stone\"\n"));
        assertTrue(stub.contains("---@alias Count integer"));
        assertTrue(stub.contains("---@field id ItemId"));
        assertEquals(1, reads.get());
        values.clear();
        assertTrue(StubGenerator.generate(registry).contains("---@alias ItemId string"));
    }

    @Test void compatibilityFlagRendersNestedIntegersAsNumbersWithoutChangingLiterals() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.root().integer("count", "Current integer count", () -> 1);
        var function = api.root().function("numbers", LuaType.map(LuaType.integer()), "");
        function.parameter("values", LuaType.list(LuaType.union(LuaType.integer(), LuaType.literal("integer"))), "");
        function.call(args -> java.util.Map.of());
        registry.freeze();

        String stub = StubGenerator.generate(registry, true);
        assertTrue(stub.contains("---@field count number Current integer count"));
        assertTrue(stub.contains("---@param values (number | \"integer\")[]"));
        assertTrue(stub.contains("---@return table<string, number>"));
        assertEquals(StubGenerator.generate(registry), StubGenerator.generate(registry, false));
        assertTrue(StubGenerator.generate(registry).contains("---@field count integer"));
    }

    @Test void registryMapKeysRetainAliasesInInputsDocumentationAndRuntime() throws Exception {
        LuaType<String> id = LuaType.stringAlias("EffectId", () -> java.util.List.of("minecraft:speed"));
        LuaType<java.util.Map<String, Integer>> effects = LuaType.map(id, LuaType.integer());
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        var model = api.model("Effects", java.util.Map.class);
        var field = model.field("values", effects, "", value -> java.util.Map.<String, Integer>of()).inputRequired();
        model.fromFields(fields -> fields.get(field));
        var echo = api.root().function("echo", effects, "");
        var argument = echo.parameter("values", effects, "");
        echo.call(args -> args.get(argument));
        registry.freeze();

        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@alias EffectId\n---| \"minecraft:speed\"\n"));
        assertTrue(stub.contains("---@field values table<EffectId, integer>"));
        assertTrue(stub.contains("---@param values table<EffectId, integer>"));
        assertTrue(StubGenerator.generate(registry, true).contains("table<EffectId, number>"));
        assertEquals("table<EffectId, integer>", effects.luaName());
        var documentation = com.google.gson.JsonParser.parseString(DocumentationGenerator.generate(registry)).getAsJsonObject();
        var parameter = documentation.getAsJsonArray("functions").get(0).getAsJsonObject()
                .getAsJsonArray("parameters").get(0).getAsJsonObject().getAsJsonObject("type");
        assertEquals("EffectId", parameter.getAsJsonObject("keyType").get("display").getAsString());
        var runtime = new dev.ifair.luahud.core.CobaltRuntime(registry);
        runtime.start();
        runtime.execute("assert(lh.echo({['minecraft:speed']=2})['minecraft:speed'] == 2)", "registry-map");
        assertThrows(IllegalArgumentException.class, () -> LuaType.map(LuaType.literal("speed"), LuaType.integer()));
    }

    private static ApiRegistry fixture() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").root().namespace("example", table -> {
            table.integer("count", "Current count", () -> 1).string("label", "Description ---@field cannot inject", () -> "x");
            FunctionBuilder<String> echo = table.function("echo", LuaType.string(), "Echo text");
            LuaParameter<String> text = echo.parameter("text", LuaType.string(), "Input text"); echo.call(args -> args.get(text));
        });
        registry.registerAddon("fake-addon", api -> { FunctionBuilder<Void> fn = api.root().function("zed", LuaType.nil(), "Addon function"); fn.call(args -> null); });
        registry.freeze(); return registry;
    }
}
