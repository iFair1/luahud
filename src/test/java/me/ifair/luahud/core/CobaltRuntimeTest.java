package me.ifair.luahud.core;

import me.ifair.luahud.api.*;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CobaltRuntimeTest {
    private record Data(String label, Integer count) {}

    @Test void valuesModelsCollectionsFunctionsAndLazyEvaluationWork() throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger modelSequence = new AtomicInteger(3);
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class)
                .string("label", "", Data::label).optionalInteger("count", "", Data::count)
                .fromTable(value -> new Data(value.string("label", ""), value.integer("count", 0)));
        api.root().namespace("nested", nested -> nested
                .integer("next", "", sequence::incrementAndGet)
                .value("nullable", LuaType.optional(LuaType.string()), LuaDoc.NONE, () -> null)
                .value("model", model.type(), LuaDoc.NONE, () -> new Data("ok", modelSequence.incrementAndGet()))
                .value("list", LuaType.list(LuaType.integer()), LuaDoc.NONE, () -> List.of(2, 3))
                .value("modelList", LuaType.list(model.type()), LuaDoc.NONE, () -> List.of(new Data("listed", 8)))
                .value("map", LuaType.map(LuaType.bool()), LuaDoc.NONE, () -> Map.of("yes", true))
                .value("modelMap", LuaType.map(model.type()), LuaDoc.NONE, () -> Map.of("entry", new Data("mapped", 9))));
        FunctionBuilder<Integer> add = api.root().function("add", LuaType.integer(), "");
        LuaParameter<Integer> left = add.parameter("left", LuaType.integer(), "");
        LuaParameter<Integer> right = add.parameter("right", LuaType.integer(), "");
        add.call(args -> args.get(left) + args.get(right));
        AtomicReference<Data> captured = new AtomicReference<>();
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Data> data = capture.parameter("data", model.type(), "");
        capture.call(args -> { captured.set(args.get(data)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("a=lh.nested.next; b=lh.nested.next; held=lh.nested.model; label=held.label; count=held.count; held.count=10; lh.capture(held); sum=lh.add(5,7); nullable=lh.nested.nullable; listv=lh.nested.list[2]; listed=lh.nested.modelList[1].label; zeroNil=lh.nested.modelList[0]==nil; mapv=lh.nested.map.yes; mapped=lh.nested.modelMap.entry.label", "test");
        assertEquals(1, runtime.global("a").checkInteger());
        assertEquals(2, runtime.global("b").checkInteger());
        assertEquals("ok", runtime.global("label").checkString());
        assertEquals(5, runtime.global("count").checkInteger());
        assertEquals(new Data("ok", 10), captured.get());
        assertEquals(12, runtime.global("sum").checkInteger());
        assertTrue(runtime.global("nullable").isNil());
        assertEquals(3, runtime.global("listv").checkInteger());
        assertEquals("listed", runtime.global("listed").checkString());
        assertTrue(runtime.global("zeroNil").checkBoolean());
        assertTrue(runtime.global("mapv").checkBoolean());
        assertEquals("mapped", runtime.global("mapped").checkString());
    }

    @Test void typedInputAndProviderDiagnosticsAreLuaErrors() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture_mod");
        LuaType<LuaTableValue> options = LuaType.inputTable("Options", fields -> fields.field("name", LuaType.string(), ""));
        FunctionBuilder<String> echo = api.root().function("echo", LuaType.string(), "");
        LuaParameter<LuaTableValue> input = echo.parameter("options", options, "");
        echo.call(args -> args.get(input).string("name", ""));
        api.root().integer("boom", "", () -> { throw new IllegalStateException("bad supplier"); });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("answer=lh.echo({name='hello'})", "ok");
        assertEquals("hello", runtime.global("answer").checkString());
        LuaError argument = assertThrows(LuaError.class, () -> runtime.execute("lh.echo({})", "bad"));
        assertTrue(argument.getMessage().contains("missing field 'name'"));
        LuaError provider = assertThrows(LuaError.class, () -> runtime.execute("return lh.boom", "boom"));
        assertTrue(provider.getMessage().contains("fixture_mod"));
        assertTrue(provider.getMessage().contains("lh.boom"));
    }

    @Test void unionKeepsNumericValuesNumericWhenStringIsListedFirst() throws Exception {
        AtomicReference<Object> captured = new AtomicReference<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Object> value = capture.parameter("value", LuaType.union(LuaType.string(), LuaType.integer()), "");
        capture.call(arguments -> {
            captured.set(arguments.get(value));
            return null;
        });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("lh.capture(0xFF00FF00)", "numeric-color");

        assertEquals(0xFF00FF00, captured.get());
    }

    @Test void unionAcceptsEitherAStringOrAModel() throws Exception {
        Data supplied = new Data("model", 4);
        List<Object> captured = new java.util.ArrayList<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class)
                .string("label", "", Data::label).integer("count", "", Data::count)
                .fromTable(value -> new Data(value.string("label", ""), value.integer("count", 0)));
        api.root().value("data", model.type(), LuaDoc.NONE, () -> supplied);
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Object> value = capture.parameter(
                "value", LuaType.union(LuaType.string(), model.type()), "");
        capture.call(arguments -> {
            captured.add(arguments.get(value));
            return null;
        });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("lh.capture('minecraft:stone'); lh.capture(lh.data)", "union-model");

        assertEquals(List.of("minecraft:stone", supplied), captured);
    }

    @Test void modelsAcceptPlainTablesAndEnforceOnlyRequiredFields() throws Exception {
        AtomicReference<Data> captured = new AtomicReference<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class)
                .string("label", "", Data::label)
                .optionalInteger("count", "", Data::count)
                .fromTable(value -> new Data(value.string("label", ""), value.integer("count", 1)));
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Data> data = capture.parameter("data", model.type(), "");
        capture.call(arguments -> { captured.set(arguments.get(data)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("lh.capture({label='plain'})", "plain-model");
        assertEquals(new Data("plain", 1), captured.get());

        LuaError error = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture({count=4})", "missing-model-field"));
        assertTrue(error.getMessage().contains("missing field 'label'"));
    }

    @Test void untouchedAndOverriddenModelFieldsStayLazy() throws Exception {
        AtomicInteger expensiveReads = new AtomicInteger();
        AtomicReference<Data> captured = new AtomicReference<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class)
                .string("label", "", Data::label)
                .optionalInteger("count", "", value -> {
                    expensiveReads.incrementAndGet();
                    return value.count();
                })
                .fromTable(value -> new Data(value.string("label", ""), value.integer("count", 1)));
        api.root().value("data", model.type(), LuaDoc.NONE, () -> new Data("lazy", 7));
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Data> data = capture.parameter("data", model.type(), "");
        capture.call(arguments -> { captured.set(arguments.get(data)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("value=lh.data; label=value.label; value.count=10; lh.capture(value)", "lazy-model");

        assertEquals("lazy", runtime.global("label").checkString());
        assertEquals(new Data("lazy", 10), captured.get());
        assertEquals(0, expensiveReads.get());
    }

    @Test void publicApiStringsAreUtf8InBothDirections() throws Exception {
        String text = "§2Yeşil: çığöşü 🌿";
        List<Object> captured = new java.util.ArrayList<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        api.root().string("localized", "", () -> text);
        FunctionBuilder<Void> captureString = api.root().function("captureString", LuaType.nil(), "");
        LuaParameter<String> string = captureString.parameter("value", LuaType.string(), "");
        captureString.call(arguments -> {
            captured.add(arguments.get(string));
            return null;
        });
        FunctionBuilder<Void> captureAny = api.root().function("captureAny", LuaType.nil(), "");
        LuaParameter<Object> any = captureAny.parameter("value", LuaType.any(), "");
        captureAny.call(arguments -> {
            captured.add(arguments.get(any));
            return null;
        });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("lh.captureString('" + text + "'); lh.captureString(lh.localized); "
                + "lh.captureAny('" + text + "'); lh.captureString(string.char(167) .. '2legacy')", "utf8");

        assertEquals(List.of(text, text, text, "§2legacy"), captured);
    }

    @Test void eventsTimersReloadAndSafeLibrariesWork() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {}); registry.addEventFunctions(); registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("ticks=0; intervals=0; lh.on('tick', function() ticks=ticks+1 end); lh.setInterval(2,'ticks',function() intervals=intervals+1 end); unsafe=(io~=nil or os~=nil or package~=nil or debug~=nil or luajava~=nil)", "callbacks");
        runtime.fireEvent("tick"); runtime.tickTimers(); runtime.tickTimers();
        assertEquals(1, runtime.global("ticks").checkInteger());
        assertEquals(1, runtime.global("intervals").checkInteger());
        assertFalse(runtime.global("unsafe").checkBoolean());
        runtime.start();
        assertTrue(runtime.global("ticks").isNil());
        runtime.fireEvent("tick"); runtime.tickTimers(); runtime.tickTimers();
        assertTrue(runtime.global("intervals").isNil());
    }

    @Test void callbackErrorsKeepProfileProvenanceAndDeduplicate() throws Exception {
        ErrorManager.beginProfileReload();
        ApiRegistry registry = new ApiRegistry(ignored -> {}); registry.addEventFunctions(); registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute(new java.io.ByteArrayInputStream(
                        "lh.on('tick', function() error('target is nil') end)".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "@Example.lua", "Example");

        runtime.fireEvent("tick");
        runtime.fireEvent("tick");

        ErrorEntry error = ErrorManager.snapshot().stream()
                .filter(entry -> entry.group().equals("Example"))
                .findFirst().orElseThrow();
        assertEquals("tick", error.phase());
        assertEquals(2, error.count());
        assertTrue(error.message().contains("target is nil"));
        assertEquals(1, ErrorManager.drainNotifications().stream()
                .filter(entry -> entry.group().equals("Example")).count());
    }

    @Test void callbacksRegisteredInsideCallbacksInheritProfile() throws Exception {
        ErrorManager.beginProfileReload();
        ApiRegistry registry = new ApiRegistry(ignored -> {}); registry.addEventFunctions(); registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute(new java.io.ByteArrayInputStream(
                        "lh.on('tick', function() lh.on('later', function() error('nested') end) end)"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "@Nested.lua", "Nested");

        runtime.fireEvent("tick");
        runtime.fireEvent("later");

        assertTrue(ErrorManager.snapshot().stream()
                .anyMatch(entry -> entry.group().equals("Nested") && entry.message().contains("nested")));
    }

    @Test void repeatedCallbackErrorsDoNotPoisonOtherProfilesOrGrowTheTraceback() throws Exception {
        ErrorManager.beginProfileReload();
        ApiRegistry registry = new ApiRegistry(ignored -> {}); registry.addEventFunctions(); registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute(new java.io.ByteArrayInputStream(
                        "lh.on('tick', function() error('target is nil') end)"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "@Broken.lua", "Broken");
        runtime.execute(new java.io.ByteArrayInputStream(
                        "healthy=0; lh.on('tick', function() healthy=healthy+1 end)"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "@Healthy.lua", "Healthy");

        for (int i = 0; i < 1000; i++) runtime.fireEvent("tick");

        assertEquals(1000, runtime.global("healthy").checkInteger());
        ErrorEntry error = ErrorManager.snapshot().stream()
                .filter(entry -> entry.group().equals("Broken"))
                .findFirst().orElseThrow();
        assertEquals(1000, error.count());
        assertFalse(error.message().contains("stack overflow"));
        assertEquals(1, ErrorManager.snapshot().stream()
                .filter(entry -> entry.group().equals("Broken")).count());
    }
}
