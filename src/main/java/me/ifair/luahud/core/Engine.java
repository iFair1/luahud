package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.config.FileWatcher;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Engine {
    public static Globals globals;
    public static LuaTable lh;

    private final AtomicBoolean needsReload = new AtomicBoolean(false);
    private WatchService watchService;

    private final FileWatcher fileWatcher = new FileWatcher();

    public void init() {
        fileWatcher.start();
        reloadAll();
    }

    public void reloadAll() {
        globals = JsePlatform.standardGlobals();
        lh = new LuaTable();

        EventDispatcher.clearAll();
        TimerManager.clearAll();

        EventDispatcher.registerToLua(lh);
        TimerManager.registerToLua(lh);

        ApiRegistry.registerAll(lh);

        globals.set("lh", lh);

        for (Map.Entry<String, ConfigData.ProfileData> entry : ConfigManager.getProfiles().entrySet()) {
            ConfigData.ProfileData profile = entry.getValue();
            if (!profile.active || profile.hidden) continue;

            runProfile(entry.getKey());
        }

        EventDispatcher.fireEvent("init");
    }

    public void runProfile(String profileName) {
        runScript(ConfigManager.getScriptsDir().resolve(profileName + ".lua"));
    }

    public void runScript(Path scriptFile) {
        if (Files.notExists(scriptFile)) {
            LuaHUD.LOGGER.error("Couldn't find the main.lua file at {}", scriptFile.toAbsolutePath());
            return;
        }

        try (InputStream stream = Files.newInputStream(scriptFile)) {
            LuaValue chunk = globals.load(stream, "@" + scriptFile.getFileName(), "bt", globals);
            chunk.call();
            LuaHUD.LOGGER.info("Script loaded successfully at {}", scriptFile.toAbsolutePath());
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while loading the script {}: {}", scriptFile.getFileName(), e.getMessage());
        }
    }

    // This should be called only by the main thread every tick. It doesn't read/write anything, only checks a boolean
    public void checkForUpdates() {
        if (fileWatcher.consumeReloadSignal()) {
            LuaHUD.LOGGER.info("A change was detected in the scripts directory. HUD is reloading...");
            reloadAll();
        }
    }

    public void shutdown() {
        fileWatcher.stop();
    }


    public static LuaTable getLhTable() {
        return lh;
    }
}