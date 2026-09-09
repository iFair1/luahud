package me.ifair.luahud.core;

import me.ifair.luahud.api.*;
import me.ifair.luahud.api.core.ApiRegistry;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CobaltRuntimeTest {
    private record Data(String label, Integer count) {}

    @Test void valuesModelsCollectionsFunctionsAndLazyEvaluationWork() throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger modelSequence = new AtomicInteger(3);
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("fixture");
        ModelBuilder<Data> model = api.model("Data", Data.class)
                .string("label", "", Data::label).integer("count", "", Data::count);
        api.root().namespace("nested", nested -> nested
                .integer("next", "", sequence::incrementAndGet)
                .value("nullable", LuaType.optional(LuaType.string()), LuaDoc.NONE, () -> null)
                .value("model", model.type(), LuaDoc.NONE, () -> new Data("ok", modelSequence.incrementAndGet()))
                .value("list", LuaType.list(LuaType.integer()), LuaDoc.NONE, () -> List.of(2, 3))
                .value("map", LuaType.map(LuaType.bool()), LuaDoc.NONE, () -> Map.of("yes", true)));
        FunctionBuilder<Integer> add = api.root().function("add", LuaType.integer(), "");
        LuaParameter<Integer> left = add.parameter("left", LuaType.integer(), "");
        LuaParameter<Integer> right = add.parameter("right", LuaType.integer(), "");
        add.call(args -> args.get(left) + args.get(right));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry); runtime.start();
        runtime.execute("a=lh.nested.next; b=lh.nested.next; held=lh.nested.model; label=held.label; count=held.count; sum=lh.add(5,7); nullable=lh.nested.nullable; listv=lh.nested.list[2]; mapv=lh.nested.map.yes", "test");
        assertEquals(1, runtime.global("a").checkInteger());
        assertEquals(2, runtime.global("b").checkInteger());
        assertEquals("ok", runtime.global("label").checkString());
        assertEquals(5, runtime.global("count").checkInteger());
        assertEquals(12, runtime.global("sum").checkInteger());
        assertTrue(runtime.global("nullable").isNil());
        assertEquals(3, runtime.global("listv").checkInteger());
        assertTrue(runtime.global("mapv").checkBoolean());
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
}
