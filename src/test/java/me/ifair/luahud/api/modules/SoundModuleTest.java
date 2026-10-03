package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.ModelBuilder;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.core.StubGenerator;
import me.ifair.luahud.core.CobaltRuntime;
import me.ifair.luahud.sound.RecordTracker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SoundModuleTest {
    @Test void soundStubsDeclareNullableRecordAndAllFields() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        SoundModule.register(registry.registrar("luahud"), null);
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@field mood number"));
        assertTrue(stub.contains("---@field record PlayingRecord?"));
        for (String field : new String[] {"length", "elapsed", "remaining"}) {
            assertTrue(stub.contains("---@field " + field + " number"));
        }
        for (String field : new String[] {"id", "name"}) {
            assertTrue(stub.contains("---@field " + field + " string"));
        }
    }

    @Test void recordModelIsReadableFromLuaAndAllowsNil() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        SoundModule.register(api, null);
        @SuppressWarnings("unchecked")
        var model = (ModelBuilder<RecordTracker.PlayingRecord>) registry.models().getFirst();
        AtomicReference<RecordTracker.PlayingRecord> current = new AtomicReference<>();
        api.root().value("testRecord", LuaType.optional(model.type()), LuaDoc.NONE, current::get);
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("assert(lh.testRecord == nil)", "empty-record");
        current.set(new RecordTracker.PlayingRecord("minecraft:music_disc_cat", "C418 - cat", 185, 25.5));
        runtime.execute("local r=lh.testRecord; assert(r.id == 'minecraft:music_disc_cat'); "
                + "assert(r.name == 'C418 - cat'); assert(r.length == 185); "
                + "assert(r.elapsed == 25.5); assert(r.remaining == 159.5)", "playing-record");
    }
}
