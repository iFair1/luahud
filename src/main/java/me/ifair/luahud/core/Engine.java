package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.modules.RenderModule;
import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.config.FileWatcher;
import me.ifair.luahud.error.ErrorManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Engine {
    private final ApiRegistry registry;
    private final FileWatcher fileWatcher = new FileWatcher();
    private CobaltRuntime runtime;
    public Engine(ApiRegistry registry) { this.registry = registry; }
    public void init() { fileWatcher.start(); reloadAll(); }
    public void reloadAll() {
        ErrorManager.beginProfileReload();
        try {
            CobaltRuntime replacement = new CobaltRuntime(registry, RenderModule::resetSection);
            replacement.start();
            runtime = replacement;
        }
        catch (Exception error) { runtime = null; ErrorManager.reportSystem("runtime", error); return; }
        for (ConfigData.ProfileData profile : ConfigManager.getProfiles()) if (profile.isActive()) runProfile(profile.getName());
        fireEvent("init");
    }
    public void runProfile(String name) { runScript(ConfigManager.getScriptsDir().resolve(name + ".lua"), name); }
    public void runScript(Path file) { runScript(file, stripExtension(file.getFileName().toString())); }
    private void runScript(Path file, String profile) {
        if (Files.notExists(file)) {
            String message = "Couldn't find the Lua profile at " + file.toAbsolutePath();
            ErrorManager.reportProfile(profile, "load", message);
            return;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            runtime.execute(stream, "@" + file.getFileName(), profile);
            LuaHUD.LOGGER.info("Script loaded successfully at {}", file.toAbsolutePath());
        } catch (Exception error) {
            ErrorManager.reportProfile(profile, "load", error);
        }
    }
    public void checkForUpdates() { if (fileWatcher.consumeReloadSignal() || ConfigManager.consumeProfileReloadSignal()) { LuaHUD.LOGGER.info("A profile or script change was detected. HUD is reloading..."); reloadAll(); } }
    public boolean isReady() { return runtime != null && runtime.isReady(); }
    public void fireEvent(String event) { if (isReady()) runtime.fireEvent(event); }
    public void tickTimers() { if (isReady()) runtime.tickTimers(); }
    public void shutdown() { fileWatcher.stop(); }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(".lua") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
