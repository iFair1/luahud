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
