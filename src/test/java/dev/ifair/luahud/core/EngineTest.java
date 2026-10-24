package dev.ifair.luahud.core;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.models.ErrorModel;
import dev.ifair.luahud.error.ErrorEntry;
import dev.ifair.luahud.error.ErrorManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    @TempDir Path directory;

    @Test void profilesAndReloadWaitForWorldEntryAndRestartOnReentry() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "loads=1; initialized=0; ticks=0; timers=0; "
                + "lh.on('reload', function() initialized=initialized+1 end); "
                + "lh.on('tick', function() assert(initialized==1); ticks=ticks+1 end); "
                + "lh.setInterval(1,'ticks',function() assert(initialized==1); timers=timers+1 end)");
        AtomicBoolean inWorld = new AtomicBoolean();
        AtomicBoolean reloadRequested = new AtomicBoolean(true);
        AtomicInteger creations = new AtomicInteger();
        AtomicReference<CobaltRuntime> current = new AtomicReference<>();
        Engine engine = engine(inWorld, reloadRequested, creations, current, profile);

        engine.checkForUpdates();
        engine.reloadAll();
        engine.runScript(profile);
        engine.fireEvent("reload");
        engine.tickTimers();
        assertFalse(engine.isReady());
        assertEquals(0, creations.get());
        assertTrue(reloadRequested.get());

        inWorld.set(true);
        engine.checkForUpdates();
        CobaltRuntime first = current.get();
        assertTrue(engine.isReady());
        assertEquals(1, creations.get());
        assertEquals(1, first.global("loads").checkInteger());
        assertEquals(1, first.global("initialized").checkInteger());
        engine.fireEvent("tick");
        engine.tickTimers();
        engine.checkForUpdates();
        assertEquals(1, creations.get());
        assertEquals(1, first.global("ticks").checkInteger());
        assertEquals(1, first.global("timers").checkInteger());

        // Execution must stop immediately, before the next lifecycle tick.
        inWorld.set(false);
        engine.fireEvent("tick");
        engine.tickTimers();
        engine.runScript(profile);
        assertFalse(engine.isReady());
        assertEquals(1, first.global("ticks").checkInteger());
        assertEquals(1, first.global("timers").checkInteger());
        engine.checkForUpdates();

        inWorld.set(true);
        engine.checkForUpdates();
        assertEquals(2, creations.get());
        assertNotSame(first, current.get());
        assertEquals(1, current.get().global("initialized").checkInteger());
        assertEquals(0, current.get().global("ticks").checkInteger());
        assertEquals(0, current.get().global("timers").checkInteger());
    }

    @Test void menuChangesAreLoadedOnEntryAndInWorldReloadRunsReloadOnce() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "version=1");
        AtomicBoolean inWorld = new AtomicBoolean(true);
        AtomicBoolean reloadRequested = new AtomicBoolean();
        AtomicInteger creations = new AtomicInteger();
        AtomicReference<CobaltRuntime> current = new AtomicReference<>();
        Engine engine = engine(inWorld, reloadRequested, creations, current, profile);
        engine.checkForUpdates();

        Files.writeString(profile, "version=2; initialized=0; lh.on('reload', function() initialized=initialized+1 end)");
        reloadRequested.set(true);
        engine.checkForUpdates();
        engine.checkForUpdates();
        assertEquals(2, creations.get());
        assertEquals(2, current.get().global("version").checkInteger());
        assertEquals(1, current.get().global("initialized").checkInteger());

        inWorld.set(false);
        engine.checkForUpdates();
        Files.writeString(profile, "version=3; initialized=0; lh.on('reload', function() initialized=initialized+1 end)");
        reloadRequested.set(true);
        engine.reloadAll();
        engine.checkForUpdates();
        assertEquals(2, creations.get());
        inWorld.set(true);
        engine.checkForUpdates();
        assertEquals(3, creations.get());
        assertEquals(3, current.get().global("version").checkInteger());
        assertEquals(1, current.get().global("initialized").checkInteger());
    }

    @Test void manualReloadReadsEditedProfilesWithoutAFileWatcherSignal() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "version=1");
        AtomicBoolean inWorld = new AtomicBoolean(true);
        AtomicBoolean reloadRequested = new AtomicBoolean();
        AtomicInteger creations = new AtomicInteger();
        AtomicReference<CobaltRuntime> current = new AtomicReference<>();
        Engine engine = engine(inWorld, reloadRequested, creations, current, profile);
        engine.checkForUpdates();
        CobaltRuntime first = current.get();

        Files.writeString(profile, "version=2; initialized=0; lh.on('reload', function() initialized=initialized+1 end)");
        engine.reloadAll();
        engine.checkForUpdates();

        assertTrue(engine.isReady());
        assertEquals(2, creations.get());
        assertNotSame(first, current.get());
        assertEquals(2, current.get().global("version").checkInteger());
        assertEquals(1, current.get().global("initialized").checkInteger());
    }

    @Test void reloadSignalsCanRecoverAnUnreadyRuntime() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "loaded=true");
        AtomicBoolean reloadRequested = new AtomicBoolean();
        AtomicInteger attempts = new AtomicInteger();
        ApiRegistry registry = registry();
        Engine engine = new Engine(() -> true, () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Startup failed");
            return new CobaltRuntime(registry);
        }, value -> value.runScript(profile), () -> reloadRequested.getAndSet(false));

        engine.checkForUpdates();
        assertFalse(engine.isReady());
        engine.checkForUpdates();
        assertEquals(1, attempts.get());
        reloadRequested.set(true);
        engine.checkForUpdates();
        assertTrue(engine.isReady());
        assertEquals(2, attempts.get());
        ErrorManager.beginProfileReload();
    }

    @Test void errorEventsDeliverOccurrencesAfterProfilesLoadAndDoNotRecurse() throws Exception {
        ErrorManager.drainEvents();
        Path broken = directory.resolve("Broken.lua");
        Path observer = directory.resolve("Observer.lua");
        Files.writeString(broken, "error('load failed')");
        Files.writeString(observer, """
                errors = {}
                reloaded = false
                lh.on('reload', function() reloaded = true end)
                lh.on('error', function(err)
                    assert(reloaded)
                    errors[#errors + 1] = err
                    error('observer failed')
                end)
                lh.on('tick', function() error('tick failed') end)
                lh.setInterval(1, 'ticks', function() error('timer failed') end)
                """);
        ApiRegistry registry = registry();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        Engine engine = new Engine(() -> true, () -> runtime, value -> {
            value.runScript(broken);
            value.runScript(observer);
        }, () -> false);

        try {
            engine.checkForUpdates();
            runtime.execute("""
                    assert(#errors == 1)
                    assert(errors[1].phase == 'load' and errors[1].isNew)
                    assert(errors[1].line == 1)
                    assert(errors[1].message == 'load failed')
                    """, "verify-load");
            engine.checkForUpdates();
            runtime.execute("assert(#errors == 1)", "verify-no-recursion");
            engine.fireEvent("tick");
            engine.fireEvent("tick");
            engine.tickTimers();
            engine.checkForUpdates();
            runtime.execute("""
                    assert(#errors == 4)
                    assert(errors[2].id == errors[3].id)
                    assert(errors[2].count == 1 and errors[2].isNew)
                    assert(errors[3].count == 2 and not errors[3].isNew)
                    assert(errors[2].group == 'Observer' and not errors[2].system)
                    assert(errors[2].phase == 'tick')
                    assert(errors[2].line == 9)
                    assert(errors[2].message == 'tick failed')
                    assert(errors[4].phase == 'timer')
                    """, "verify-occurrences");
            ErrorEntry handlerError = ErrorManager.snapshot().stream()
                    .filter(error -> error.group().equals("Observer") && error.phase().equals("error"))
                    .findFirst().orElseThrow();
            assertEquals(4, handlerError.count());

            ErrorManager.reportSystem("error-event-test", "system failure");
            engine.checkForUpdates();
            runtime.execute("""
                    assert(#errors == 5)
                    assert(errors[5].system and errors[5].group == 'LuaHUD')
                    assert(errors[5].line == -1)
                    assert(errors[5].message == 'system failure')
                    """, "verify-system");
            engine.checkForUpdates();
            runtime.execute("assert(#errors == 5)", "verify-no-delayed-recursion");
        } finally {
            engine.shutdown();
            ErrorManager.beginProfileReload();
            for (ErrorEntry error : ErrorManager.snapshot()) {
                if (error.phase().equals("error-event-test")) ErrorManager.dismiss(error.id());
            }
        }
    }

    private static Engine engine(AtomicBoolean inWorld, AtomicBoolean reloadRequested,
                                 AtomicInteger creations, AtomicReference<CobaltRuntime> current, Path profile) {
        ApiRegistry registry = registry();
        return new Engine(inWorld::get, () -> {
            creations.incrementAndGet();
            CobaltRuntime runtime = new CobaltRuntime(registry);
            current.set(runtime);
            return runtime;
        }, value -> value.runScript(profile), () -> reloadRequested.getAndSet(false));
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.registrar("luahud").event("reload", "");
        registry.registrar("luahud").event("tick", "");
        registry.registrar("luahud").event("render", "");
        var api = registry.registrar("luahud");
        api.event("error", ErrorModel.register(api).type(), "");
        registry.addEventFunctions();
        registry.freeze();
        return registry;
    }
}
