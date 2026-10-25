package dev.ifair.luahud.core;

import dev.ifair.luahud.api.*;
import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.error.ErrorEntry;
import dev.ifair.luahud.error.ErrorManager;
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
        ModelBuilder<Data> model = api.model("Data", Data.class);
        var labelField = model.string("label", "", Data::label).inputRequired();
        var countField = model.optionalInteger("count", "", Data::count).inputDefault(0);
        model.fromFields(fields -> new Data(fields.get(labelField), fields.get(countField)));
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
        LuaParameter<Data> data = capture.parameter("data", model.inputType(), "");
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

    @Test void outputUnionsSelectMatchingCollectionsOptionalAndNestedAlternatives() throws Exception {
        record OutputCase(LuaType<Object> type, Object value, String assertion) {}
        var cases = List.of(
                new OutputCase(LuaType.union(LuaType.list(LuaType.string()), LuaType.string()), "ok", "value == 'ok'"),
                new OutputCase(LuaType.union(LuaType.map(LuaType.string()), LuaType.integer()), 7, "value == 7"),
                new OutputCase(LuaType.union(LuaType.optional(LuaType.string()), LuaType.integer()), 8, "value == 8"),
                new OutputCase(LuaType.union(LuaType.union(LuaType.string(), LuaType.bool()), LuaType.integer()), 9, "value == 9"),
                new OutputCase(LuaType.union(LuaType.list(LuaType.string()), LuaType.list(LuaType.integer())), List.of(3), "value[1] == 3"),
                new OutputCase(LuaType.union(LuaType.map(LuaType.string()), LuaType.map(LuaType.integer())), Map.of("count", 4), "value.count == 4"),
                new OutputCase(LuaType.union(LuaType.list(LuaType.string()), LuaType.map(LuaType.integer())), Map.of("count", 5), "value.count == 5"),
                new OutputCase(LuaType.union(LuaType.inputTable("Options", fields -> {}), LuaType.string()), "ok", "value == 'ok'"));
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var root = registry.registrar("fixture").root();
        for (int i = 0; i < cases.size(); i++) {
            OutputCase output = cases.get(i);
            root.function("result" + i, output.type(), "").call(args -> output.value());
            root.value("value" + i, output.type(), LuaDoc.NONE, output::value);
        }
        root.function("invalid", LuaType.union(LuaType.list(LuaType.string()), LuaType.map(LuaType.integer())), "")
                .call(args -> true);
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        for (int i = 0; i < cases.size(); i++) {
            String assertion = cases.get(i).assertion();
            runtime.execute("local value=lh.result" + i + "(); assert(" + assertion + "); "
                    + "value=lh.value" + i + "; assert(" + assertion + ")", "output-union-" + i);
        }
        LuaError error = assertThrows(LuaError.class, () -> runtime.execute("lh.invalid()", "invalid-output-union"));
        assertTrue(error.getMessage().contains("value does not match declared union"));
    }

    @Test void aliasesKeepUnderlyingRuntimeAndEventContractsWithoutReadingRegistryValues() throws Exception {
        LuaType<String> id = LuaType.stringAlias("ItemId", () -> {
            throw new AssertionError("Runtime must not read stub values");
        });
        List<Object> captured = new java.util.ArrayList<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        api.root().value("id", id, LuaDoc.NONE, () -> "minecraft:stone");
        FunctionBuilder<Object> echo = api.root().function("echo", LuaType.union(id, LuaType.integer()), "");
        var value = echo.parameter("value", LuaType.union(id, LuaType.integer()), "");
        echo.call(arguments -> {
            Object actual = arguments.get(value);
            captured.add(actual);
            return actual;
        });
        var event = api.event("item", id, "");
        registry.freeze();
        registry.validateEvent(event, "minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(event, 1));
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("assert(lh.echo(lh.id)=='minecraft:stone'); assert(lh.echo(42)==42)", "aliases");
        assertEquals(List.of("minecraft:stone", 42), captured);
        assertThrows(LuaError.class, () -> runtime.execute("lh.echo({})", "invalid-alias"));
    }

    @Test void unionAcceptsEitherAStringOrAModel() throws Exception {
        Data supplied = new Data("model", 4);
        List<Object> captured = new java.util.ArrayList<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class);
        var labelField = model.string("label", "", Data::label).inputRequired();
        var countField = model.integer("count", "", Data::count).inputRequired();
        model.fromFields(fields -> new Data(fields.get(labelField), fields.get(countField)));
        api.root().value("data", model.type(), LuaDoc.NONE, () -> supplied);
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Object> value = capture.parameter(
                "value", LuaType.union(LuaType.string(), model.inputType()), "");
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
        ModelBuilder<Data> model = api.model("Data", Data.class);
        var labelField = model.string("label", "", Data::label).inputRequired();
        var countField = model.optionalInteger("count", "", Data::count).inputDefault(1);
        model.fromFields(fields -> new Data(fields.get(labelField), fields.get(countField)));
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Data> data = capture.parameter("data", model.inputType(), "");
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
        ModelBuilder<Data> model = api.model("Data", Data.class);
        var labelField = model.string("label", "", Data::label).inputRequired();
        var countField = model.optionalInteger("count", "", value -> {
                    expensiveReads.incrementAndGet();
                    return value.count();
                }).inputDefault(1);
        model.fromFields(fields -> new Data(fields.get(labelField), fields.get(countField)));
        api.root().value("data", model.type(), LuaDoc.NONE, () -> new Data("lazy", 7));
        FunctionBuilder<Void> capture = api.root().function("capture", LuaType.nil(), "");
        LuaParameter<Data> data = capture.parameter("data", model.inputType(), "");
        capture.call(arguments -> { captured.set(arguments.get(data)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();

        runtime.execute("value=lh.data; label=value.label; value.count=10; lh.capture(value)", "lazy-model");

        assertEquals("lazy", runtime.global("label").checkString());
        assertEquals(new Data("lazy", 10), captured.get());
        assertEquals(0, expensiveReads.get());
    }

    @Test void modelTablesIgnoreExtraFieldsAndValidateConsumedFields() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class);
        var label = model.string("label", "", Data::label).inputRequired();
        var count = model.integer("count", "", Data::count).inputDefault(1);
        model.string("display", "", Data::label);
        model.fromFields(fields -> new Data(fields.get(label), fields.get(count)));
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("data", model.inputType(), "");
        AtomicReference<Data> captured = new AtomicReference<>();
        capture.call(args -> { captured.set(args.get(argument)); return null; });
        api.root().value("data", model.type(), LuaDoc.NONE, () -> new Data("source", 5));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("lh.capture({label='ok', counnt=2, display=false, [1]='extra'})", "wide-table");
        assertEquals(new Data("ok", 1), captured.get());
        runtime.execute("value=lh.data; value.display=false; value.extra=42; lh.capture(value)", "wide-proxy");
        assertEquals(new Data("source", 5), captured.get());
        LuaError invalid = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture({label='ok', count=false})", "wrong-type"));
        assertTrue(invalid.getMessage().contains("field 'count'"));
    }

    @Test void modelInputsAcceptNamespacesAndOtherModelSchemas() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        var model = api.model("Data", Data.class);
        var label = model.string("label", "", Data::label).inputRequired();
        var count = model.integer("count", "", Data::count).inputDefault(1);
        model.fromFields(fields -> new Data(fields.get(label), fields.get(count)));
        api.root().namespace("player", player -> {
            player.string("label", "", () -> "player");
            player.namespace("xp", xp -> xp.integer("level", "", () -> 3));
        });
        var wider = api.model("WiderData", Data.class);
        wider.string("label", "", Data::label);
        wider.integer("count", "", Data::count);
        wider.bool("extra", "", value -> true);
        api.root().model("wider", wider, "", () -> new Data("wider", 7));
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("data", model.inputType(), "");
        AtomicReference<Data> captured = new AtomicReference<>();
        capture.call(args -> { captured.set(args.get(argument)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        runtime.execute("lh.capture(lh.player)", "namespace-input");
        assertEquals(new Data("player", 1), captured.get());
        runtime.execute("lh.capture(lh.wider)", "different-model-input");
        assertEquals(new Data("wider", 7), captured.get());
        runtime.execute("lh.capture(setmetatable({count=8}, {__index={label='inherited'}}))", "table-index");
        assertEquals(new Data("inherited", 8), captured.get());
        runtime.execute("lh.capture(setmetatable({}, {__index=function(_, key) if key=='label' then return 'computed' end end}))", "function-index");
        assertEquals(new Data("computed", 1), captured.get());
        LuaError missing = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture({count=2, extra=true})", "missing-required"));
        assertTrue(missing.getMessage().contains("missing field 'label'"));
        LuaError invalid = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture(setmetatable({}, {__index={label='ok', count=false}}))", "invalid-index"));
        assertTrue(invalid.getMessage().contains("field 'count'"));
        LuaError lookup = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture(setmetatable({}, {__index=function() error('lookup failed') end}))", "failed-index"));
        assertTrue(lookup.getMessage().contains("field 'label'"));
        assertTrue(lookup.getMessage().contains("lookup failed"));
    }

    @Test void optionsTablesRejectUnknownAndNonStringKeys() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        var options = LuaType.inputTable("Options", fields -> fields.optional("count", LuaType.integer(), ""));
        var capture = api.root().function("capture", LuaType.nil(), "");
        capture.parameter("options", options, "");
        capture.call(args -> null);
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        runtime.execute("lh.capture({count=2}); lh.capture({})", "valid-options");
        LuaError unknown = assertThrows(LuaError.class,
                () -> runtime.execute("lh.capture({counnt=2})", "unknown-option"));
        assertTrue(unknown.getMessage().contains("unknown field 'counnt' in Options"));
        assertThrows(LuaError.class, () -> runtime.execute("lh.capture({[1]=2})", "numeric-option"));
        assertThrows(LuaError.class, () -> runtime.execute("lh.capture({count=false})", "invalid-option"));
    }

    @Test void unchangedProxyPassesOriginalObjectWithoutReadingFields() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        AtomicInteger reads = new AtomicInteger();
        ModelBuilder<Data> model = api.model("Data", Data.class);
        model.string("label", "", value -> { reads.incrementAndGet(); return value.label(); });
        Data original = new Data("source", 7);
        api.root().value("data", model.type(), LuaDoc.NONE, () -> original);
        AtomicReference<Data> captured = new AtomicReference<>();
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("data", model.inputType(), "");
        capture.call(args -> { captured.set(args.get(argument)); return null; });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("lh.capture(lh.data)", "reference");
        assertSame(original, captured.get());
        assertEquals(0, reads.get());
        assertThrows(LuaError.class, () -> runtime.execute("lh.capture({})", "plain-output-model"));
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
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("ticks=0; intervals=0; lh.on('tick', function() ticks=ticks+1 end); lh.setInterval(2,'tick',function() intervals=intervals+1 end); unsafe=(io~=nil or os~=nil or debug~=nil or luajava~=nil)", "callbacks");
        runtime.fireEvent("tick"); runtime.fireEvent("tick");
        assertEquals(2, runtime.global("ticks").checkInteger());
        assertEquals(1, runtime.global("intervals").checkInteger());
        assertFalse(runtime.global("unsafe").checkBoolean());
        runtime.start();
        assertTrue(runtime.global("ticks").isNil());
        runtime.fireEvent("tick"); runtime.fireEvent("tick");
        assertTrue(runtime.global("intervals").isNil());
    }

    @Test void intervalsCountOnlyTheirSelectedEventAndPassItsPayload() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        api.event("tick", "");
        api.event("render", "");
        var damage = api.event("damage", LuaType.integer(), "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("""
                ticks = 0; renders = 0; damages = 0; lastDamage = 0
                lh.setInterval(2, 'tick', function() ticks = ticks + 1 end)
                lh.setInterval(3, 'render', function() renders = renders + 1 end)
                lh.setInterval(2, 'damage', function(value)
                    damages = damages + 1
                    lastDamage = value
                end)
                """, "intervals");

        runtime.fireEvent("tick");
        runtime.fireEvent("render");
        runtime.fireEvent("damage", 4);
        runtime.fireEvent("render");
        assertEquals(0, runtime.global("ticks").checkInteger());
        assertEquals(0, runtime.global("renders").checkInteger());
        assertEquals(0, runtime.global("damages").checkInteger());

        runtime.fireEvent("tick");
        runtime.fireEvent("render");
        runtime.fireEvent(damage, 7);
        assertEquals(1, runtime.global("ticks").checkInteger());
        assertEquals(1, runtime.global("renders").checkInteger());
        assertEquals(1, runtime.global("damages").checkInteger());
        assertEquals(7, runtime.global("lastDamage").checkInteger());

        runtime.fireEvent("render");
        runtime.fireEvent("render");
        assertEquals(1, runtime.global("renders").checkInteger());
        runtime.fireEvent("render");
        assertEquals(2, runtime.global("renders").checkInteger());
    }

    @Test void intervalsRejectUnregisteredEventNames() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("fixture").event("tick", "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        for (String event : List.of("missing", "ticks")) {
            LuaError error = assertThrows(LuaError.class,
                    () -> runtime.execute("lh.setInterval(20, '" + event + "', function() end)", "invalid"));
            assertTrue(error.getMessage().contains("Unknown event: " + event));
        }
    }

    @Test void watchdogStopsLoopsAndCannotBeHiddenByPcall() throws Exception {
        AtomicInteger clock = new AtomicInteger();
        ApiRegistry registry = watchdogRegistry(clock);
        CobaltRuntime runtime = new CobaltRuntime(registry, () -> {}, 25,
                () -> clock.get() * 1_000_000L);
        runtime.start();

        LuaError direct = assertThrows(LuaError.class,
                () -> runtime.execute("lh.expire(); while true do end", "direct-timeout"));
        assertTrue(direct.getMessage().contains("exceeded 25 ms"));

        clock.set(0);
        LuaError protectedCall = assertThrows(LuaError.class,
                () -> runtime.execute("lh.expire(); pcall(function() while true do end end)", "pcall-timeout"));
        assertTrue(protectedCall.getMessage().contains("exceeded 25 ms"));

        clock.set(0);
        runtime.execute("watchdogRecovered = true", "healthy");
        assertTrue(runtime.global("watchdogRecovered").checkBoolean());
    }

    @Test void zeroTimeoutDisablesTheWatchdog() throws Exception {
        AtomicInteger clock = new AtomicInteger();
        ApiRegistry registry = watchdogRegistry(clock);
        CobaltRuntime runtime = new CobaltRuntime(registry, () -> {}, 0,
                () -> clock.get() * 1_000_000L);
        runtime.start();

        runtime.execute("lh.expire(); local total=0; for i=1,100000 do total=total+i end; completed=true", "disabled");

        assertTrue(runtime.global("completed").checkBoolean());
    }

    @Test void timedOutLoadRollsBackItsEventAndTimerRegistrations() throws Exception {
        AtomicInteger clock = new AtomicInteger();
        ApiRegistry registry = watchdogRegistry(clock);
        CobaltRuntime runtime = new CobaltRuntime(registry, () -> {}, 25,
                () -> clock.get() * 1_000_000L);
        runtime.start();

        assertThrows(LuaError.class, () -> runtime.execute(new java.io.ByteArrayInputStream((
                        "events=0; timers=0; "
                                + "lh.on('tick', function() events=events+1 end); "
                                + "lh.setInterval(1,'tick',function() timers=timers+1 end); "
                                + "lh.expire(); while true do end")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)), "@Broken.lua", "Broken"));

        clock.set(0);
        runtime.fireEvent("tick");
        assertEquals(0, runtime.global("events").checkInteger());
        assertEquals(0, runtime.global("timers").checkInteger());
    }

    @Test void timedOutCallbacksAreDroppedWithoutAffectingHealthyCallbacks() throws Exception {
        ErrorManager.beginProfileReload();
        AtomicInteger clock = new AtomicInteger();
        ApiRegistry registry = watchdogRegistry(clock);
        CobaltRuntime runtime = new CobaltRuntime(registry, () -> {}, 25,
                () -> clock.get() * 1_000_000L);
        runtime.start();
        runtime.execute("eventAttempts=0; healthyEvents=0; timerAttempts=0; healthyTimers=0; "
                        + "lh.on('tick', function() eventAttempts=eventAttempts+1; lh.expire(); while true do end end); "
                        + "lh.on('tick', function() healthyEvents=healthyEvents+1 end); "
                        + "lh.setInterval(1,'render',function() timerAttempts=timerAttempts+1; lh.expire(); while true do end end); "
                        + "lh.setInterval(1,'render',function() healthyTimers=healthyTimers+1 end)",
                "callbacks");

        runtime.fireEvent("tick");
        clock.set(0);
        runtime.fireEvent("tick");
        clock.set(0);
        runtime.fireEvent("render");
        clock.set(0);
        runtime.fireEvent("render");

        assertEquals(1, runtime.global("eventAttempts").checkInteger());
        assertEquals(2, runtime.global("healthyEvents").checkInteger());
        assertEquals(1, runtime.global("timerAttempts").checkInteger());
        assertEquals(2, runtime.global("healthyTimers").checkInteger());
        assertTrue(ErrorManager.snapshot().stream()
                .anyMatch(entry -> entry.phase().equals("tick") && entry.message().contains("exceeded 25 ms")));
        assertTrue(ErrorManager.snapshot().stream()
                .anyMatch(entry -> entry.phase().equals("timer") && entry.message().contains("exceeded 25 ms")));
    }

    private static ApiRegistry watchdogRegistry(AtomicInteger clock) {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.registrar("fixture").root().function("expire", LuaType.nil(), "")
                .call(arguments -> {
                    clock.set(26);
                    return null;
                });
        registry.freeze();
        return registry;
    }

    @Test void callbackErrorsKeepProfileProvenanceAndDeduplicate() throws Exception {
        ErrorManager.beginProfileReload();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.freeze();
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
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute(new java.io.ByteArrayInputStream(
                        "lh.on('tick', function() lh.on('render', function() error('nested') end) end)"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "@Nested.lua", "Nested");

        runtime.fireEvent("tick");
        runtime.fireEvent("render");

        assertTrue(ErrorManager.snapshot().stream()
                .anyMatch(entry -> entry.group().equals("Nested") && entry.message().contains("nested")));
    }

    @Test void repeatedCallbackErrorsDoNotPoisonOtherProfilesOrGrowTheTraceback() throws Exception {
        ErrorManager.beginProfileReload();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        registry.addEventFunctions();
        registry.freeze();
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
    private record Damage(String attacker, double amount) {}

    @Test void eventModelsReachEachListenerAsOneIndependentArgument() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        var model = api.model("Damage", Damage.class);
        model.string("attacker", "", Damage::attacker);
        model.number("amount", "", Damage::amount);
        var event = api.event("damage", model.type(), "Damage payload");
        api.event("tick", "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("lh.on('damage', function(...) assert(select('#', ...)==1); "
                + "local damage=...; first=damage.amount; damage.amount=999 end); "
                + "lh.on('damage', function(damage) second=damage.amount; attacker=damage.attacker end); "
                + "lh.on('tick', function(...) tickArgs=select('#', ...) end)", "listeners");
        runtime.fireEvent(event, new Damage("player", 6.0));
        assertEquals(6.0, runtime.global("first").checkDouble());
        assertEquals(6.0, runtime.global("second").checkDouble());
        assertEquals("player", runtime.global("attacker").checkString());
        runtime.fireEvent("damage", new Damage("another", 3.0));
        assertEquals(3.0, runtime.global("second").checkDouble());
        runtime.fireEvent("tick");
        assertEquals(0, runtime.global("tickArgs").checkInteger());
    }

    @Test void unknownEventsAndIncorrectPublicationPayloadsAreRejected() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("fixture").event("count", LuaType.integer(), "");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        LuaError error = assertThrows(LuaError.class,
                () -> runtime.execute("lh.on('init', function() end)", "removed-event"));
        assertTrue(error.getMessage().contains("Unknown event: init"));
        assertThrows(IllegalArgumentException.class, () -> runtime.fireEvent("missing"));
        assertThrows(IllegalArgumentException.class, () -> runtime.fireEvent("count", "wrong"));
        assertThrows(IllegalArgumentException.class, () -> runtime.fireEvent("count"));
    }
    @Test void nestedJavaPublicationsDoNotExtendTheOuterWatchdogDeadline() throws Exception {
        AtomicInteger clock = new AtomicInteger();
        AtomicReference<CobaltRuntime> current = new AtomicReference<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        api.event("outer", "");
        api.event("inner", "");
        api.root().function("emit", LuaType.nil(), "").call(arguments -> {
            clock.set(24);
            current.get().fireEvent("inner");
            clock.set(26);
            return null;
        });
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry, () -> {}, 25, () -> clock.get() * 1_000_000L);
        current.set(runtime);
        runtime.start();
        runtime.execute("outerCalls=0; innerCalls=0; "
                + "lh.on('outer', function() outerCalls=outerCalls+1; lh.emit() end); "
                + "lh.on('inner', function() innerCalls=innerCalls+1 end)", "nested");
        runtime.fireEvent("outer");
        clock.set(0);
        runtime.fireEvent("outer");
        assertEquals(1, runtime.global("outerCalls").checkInteger());
        assertEquals(1, runtime.global("innerCalls").checkInteger());
    }
}
