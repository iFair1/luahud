package dev.ifair.luahud.core;

import dev.ifair.luahud.LuaHUD;
import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.modules.RenderModule;
import dev.ifair.luahud.config.ConfigData;
import dev.ifair.luahud.config.ConfigManager;
import dev.ifair.luahud.config.FileWatcher;
import dev.ifair.luahud.error.ErrorManager;
import dev.ifair.luahud.render.RenderBuffer;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Engine {
    private final BooleanSupplier inWorld;
    private final Supplier<CobaltRuntime> runtimeFactory;
    private final Consumer<Engine> profileLoader;
    private final BooleanSupplier reloadRequested;
    private final Supplier<Path> scriptsDirectory;
    private boolean worldActive;
    private boolean reloadPending = true;
    private final FileWatcher fileWatcher = new FileWatcher();
    private CobaltRuntime runtime;

    public Engine(ApiRegistry registry) {
        scriptsDirectory = ConfigManager::getScriptsDir;
        inWorld = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            return client.world != null && client.player != null;
        };
        runtimeFactory = () -> new CobaltRuntime(
                registry, RenderModule::resetSection, ConfigManager.getLuaTimeoutMs());
        profileLoader = engine -> {
            for (ConfigData.ProfileData profile : ConfigManager.getProfiles()) {
                if (profile.isActive()) engine.runProfile(profile.getName());
            }
        };
        reloadRequested = () -> {
            boolean filesChanged = fileWatcher.consumeReloadSignal();
            boolean profilesChanged = ConfigManager.consumeProfileReloadSignal();
            return filesChanged || profilesChanged;
        };
    }

    Engine(BooleanSupplier inWorld, Supplier<CobaltRuntime> runtimeFactory,
           Consumer<Engine> profileLoader, BooleanSupplier reloadRequested) {
        this.inWorld = inWorld;
        this.runtimeFactory = runtimeFactory;
        this.profileLoader = profileLoader;
        this.reloadRequested = reloadRequested;
        this.scriptsDirectory = () -> null;
    }

    public void init() { fileWatcher.start(); }

    public void reloadAll() {
        reloadPending = true;
        if (!inWorld.getAsBoolean()) return;
        reloadPending = false;
        RenderBuffer.clear();
        ErrorManager.beginProfileReload();
        try {
            CobaltRuntime replacement = runtimeFactory.get();
            replacement.start(scriptsDirectory.get());
            runtime = replacement;
        } catch (Exception error) {
            runtime = null;
            ErrorManager.reportSystem("runtime", error);
            return;
        }
        profileLoader.accept(this);
        fireEvent("init");
    }

    public void runProfile(String name) { runScript(ConfigManager.getScriptsDir().resolve(name + ".lua"), name); }
    public void runScript(Path file) { runScript(file, stripExtension(file.getFileName().toString())); }
    private void runScript(Path file, String profile) {
        if (!isReady()) return;
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

    /** Updates the world lifecycle even when no Lua runtime is ready yet. */
    public void checkForUpdates() {
        if (!inWorld.getAsBoolean()) {
            if (worldActive || runtime != null) {
                runtime = null;
                RenderBuffer.clear();
                RenderModule.resetSection();
            }
            worldActive = false;
            reloadPending = true;
            return;
        }
        if (!worldActive) {
            worldActive = true;
            reloadPending = true;
        }
        if (reloadRequested.getAsBoolean()) {
            LuaHUD.LOGGER.info("A profile or script change was detected. HUD is reloading...");
            reloadPending = true;
        }
        if (reloadPending) reloadAll();
    }

    public boolean isReady() { return inWorld.getAsBoolean() && runtime != null && runtime.isReady(); }
    public void fireEvent(String event) { if (isReady()) runtime.fireEvent(event); }
    public void tickTimers() { if (isReady()) runtime.tickTimers(); }
    public void shutdown() {
        fileWatcher.stop();
        runtime = null;
        worldActive = false;
        reloadPending = true;
        RenderBuffer.clear();
        RenderModule.resetSection();
    }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(".lua") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
