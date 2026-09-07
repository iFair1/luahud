package me.ifair.luahud.config;

import me.ifair.luahud.LuaHUD;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileWatcher {
    private final AtomicBoolean reloadNeeded = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    public void start() {
        stop(); // stop if already running

        try {
            watchService = FileSystems.getDefault().newWatchService();
            ConfigManager.MOD_CONFIG_DIR.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            running = true;
            watchThread = new Thread(this::runWatcher, "LuaHUD-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            LuaHUD.LOGGER.error("Could not start File-Watcher: {}", e.getMessage());
        }
    }

    private void runWatcher() {
        try {
            WatchKey key;
            while (running && (key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path changedFile = (Path) event.context();
                    String fileName = changedFile.toString();

                    if (fileName.endsWith(".lua")) reloadNeeded.set(true);
                }
                boolean valid = key.reset();

                if (!valid) break;
            }
        } catch (ClosedWatchServiceException e) {
            // Here will trigger when the watcher is closed. No problem, just skip
        } catch (Exception e) {
            LuaHUD.LOGGER.error("FileWatcher Error: {}", e.getMessage());
        }
    }

    // This should be called by main thread to see if needsReload
    public boolean consumeReloadSignal() {
        return reloadNeeded.compareAndSet(true, false);
    }

    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        if (watchThread != null && watchThread.isAlive()) {
            watchThread.interrupt();
        }
    }
}
