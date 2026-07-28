package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.PerformanceModule;
import me.ifair.luahud.api.RenderModule;
import me.ifair.luahud.api.WorldModule;
import net.fabricmc.loader.api.FabricLoader;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Engine {
    public static Globals globals;
    public static LuaTable lh;

    // for hot reloading
    private long lastModifiedTime = 0;
    private int checkTimer = 0;

    public void init() {
        // reset all old things when reload
        globals = JsePlatform.standardGlobals();
        lh = new LuaTable();

        EventDispatcher.clearAll();
        TimerManager.clearAll();

        EventDispatcher.registerToLua(lh);
        TimerManager.registerToLua(lh);

        new RenderModule().register(lh);
        new WorldModule().register(lh);
        new PerformanceModule().register(lh);

        globals.set("lh", lh);

        try {
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("luahud");
            Path scriptFile = configDir.resolve("main.lua");

            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(scriptFile)) {
                LuaHUD.LOGGER.error("Couldn't find the main.lua file at {}", scriptFile.toAbsolutePath());
                return;
            }

            // Comparing only the last edit time rather than reading through the whole file
            lastModifiedTime = Files.getLastModifiedTime(scriptFile).toMillis(); // save the last modified time in milliseconds

            try (InputStream stream = Files.newInputStream(scriptFile)) {
                LuaValue chunk = globals.load(stream, "@main.lua", "bt", globals);
                chunk.call();

                EventDispatcher.fireEvent("init");
                LuaHUD.LOGGER.info("Script loaded successfully at {}", scriptFile.toAbsolutePath());
            }
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was en error while loading the script: {}", e.getMessage());
        }
    }

    // Gets called every second
    public void checkForUpdates() {
        checkTimer++;
        if (checkTimer >= 20) { // 20 Tick = 1 Saniye
            checkTimer = 0;
            try {
                Path scriptFile = FabricLoader.getInstance().getConfigDir().resolve("luahud").resolve("main.lua");
                if (Files.exists(scriptFile)) {
                    long currentModifiedTime = Files.getLastModifiedTime(scriptFile).toMillis();

                    // currentModifiedTime should be bigger than the lastModifiedTime if the user changed the file
                    if (currentModifiedTime > lastModifiedTime) {
                        LuaHUD.LOGGER.info("A change was detected at the script file. HUD is reloading...");
                        init(); // Load everything again
                    }
                }
            } catch (Exception e) {
                LuaHUD.LOGGER.error("There was an error while checking for updates in the script file: {}", e.getMessage());
            }
        }
    }

    public static LuaTable getLhTable() {
        return lh;
    }
}