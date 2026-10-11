package dev.ifair.luahud.core;

import dev.ifair.luahud.api.core.ApiRegistry;
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

    @Test void profilesAndInitWaitForWorldEntryAndRestartOnReentry() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "loads=1; initialized=0; ticks=0; timers=0; "
                + "lh.on('init', function() initialized=initialized+1 end); "
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
        engine.fireEvent("init");
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

    @Test void menuChangesAreLoadedOnEntryAndInWorldReloadRunsInitOnce() throws Exception {
        Path profile = directory.resolve("Profile.lua");
        Files.writeString(profile, "version=1");
        AtomicBoolean inWorld = new AtomicBoolean(true);
        AtomicBoolean reloadRequested = new AtomicBoolean();
        AtomicInteger creations = new AtomicInteger();
        AtomicReference<CobaltRuntime> current = new AtomicReference<>();
        Engine engine = engine(inWorld, reloadRequested, creations, current, profile);
        engine.checkForUpdates();

        Files.writeString(profile, "version=2; initialized=0; lh.on('init', function() initialized=initialized+1 end)");
        reloadRequested.set(true);
        engine.checkForUpdates();
        engine.checkForUpdates();
        assertEquals(2, creations.get());
        assertEquals(2, current.get().global("version").checkInteger());
        assertEquals(1, current.get().global("initialized").checkInteger());

        inWorld.set(false);
        engine.checkForUpdates();
        Files.writeString(profile, "version=3; initialized=0; lh.on('init', function() initialized=initialized+1 end)");
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

        Files.writeString(profile, "version=2; initialized=0; lh.on('init', function() initialized=initialized+1 end)");
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
        registry.addEventFunctions();
        registry.freeze();
        return registry;
    }
}
