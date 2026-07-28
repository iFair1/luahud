package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.ApiRegistry;
import me.ifair.luahud.api.PerformanceModule;
import me.ifair.luahud.api.RenderModule;
import me.ifair.luahud.api.WorldModule;
import net.fabricmc.loader.api.FabricLoader;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Engine {
    public static Globals globals;
    public static LuaTable lh;

    private final AtomicBoolean needsReload = new AtomicBoolean(false);
    private WatchService watchService;

    public void init() {
        // Clear the watcher and the old thread while reload
        stopWatcher();

        // Reset old states
        globals = JsePlatform.standardGlobals();
        lh = new LuaTable();

        EventDispatcher.clearAll();
        TimerManager.clearAll();

        EventDispatcher.registerToLua(lh);
        TimerManager.registerToLua(lh);

        ApiRegistry.registerAll(lh);

        globals.set("lh", lh);

        try {
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("luahud");
            Path scriptFile = configDir.resolve("main.lua");

            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // Start file listener in the background
            startWatcher(configDir);

            if (!Files.exists(scriptFile)) {
                LuaHUD.LOGGER.error("Couldn't find the main.lua file at {}", scriptFile.toAbsolutePath());
                return;
            }

            try (InputStream stream = Files.newInputStream(scriptFile)) {
                LuaValue chunk = globals.load(stream, "@main.lua", "bt", globals);
                chunk.call();

                EventDispatcher.fireEvent("init");
                LuaHUD.LOGGER.info("Script loaded successfully at {}", scriptFile.toAbsolutePath());
            }


        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while loading the script: {}", e.getMessage());
        }
    }


    // async thread that listens the configDir
    private void startWatcher(Path configDir) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            Thread watchThread = new Thread(() -> {
                try {
                    WatchKey key;
                    while ((key = watchService.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changed = (Path) event.context();
                            if (changed.toString().equals("main.lua")) {
                                needsReload.set(true); // send reload signal to the main thread
                            }
                        }
                        key.reset();
                    }
                } catch (ClosedWatchServiceException e) {
                    // Here will trigger when the watcher is closed. No problem, just skip
                } catch (Exception e) {
                    LuaHUD.LOGGER.error("Error in file watcher thread: {}", e.getMessage());
                }
            }, "LuaHUD-FileWatcher");

            watchThread.setDaemon(true); // Make the thread automatically die when the game closes
            watchThread.start();

        } catch (Exception e) {
            LuaHUD.LOGGER.error("Failed to initialize File Watcher: {}", e.getMessage());
        }
    }

    private void stopWatcher() {
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (Exception ignored) {}
    }


    // This should be called only by the main thread every tick. It doesn't read/write anything, only checks a boolean
    public void checkForUpdates() {
        if (needsReload.compareAndSet(true, false)) {
            LuaHUD.LOGGER.info("A change was detected at main.lua. HUD is reloading...");
            init(); // Reload the script.
        }
    }

    public static LuaTable getLhTable() {
        return lh;
    }
}