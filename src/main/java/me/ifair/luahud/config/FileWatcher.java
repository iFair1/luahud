package me.ifair.luahud.config;

import me.ifair.luahud.error.ErrorManager;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileWatcher {
    private static final String LUA_EXTENSION = ".lua";

    private final AtomicBoolean reloadNeeded = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    public void start() {
        stop(); // stop if already running

        try {
            if (Files.notExists(ConfigManager.MOD_CONFIG_DIR)) {
                Files.createDirectories(ConfigManager.MOD_CONFIG_DIR);
            }

            syncExistingFiles();

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
            ErrorManager.reportSystem("file watcher", e);
        }
    }

    // Make sure every .lua file in the MOD_CONFIG_DIR matches someone in the luahud.json
    private void syncExistingFiles() {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(ConfigManager.MOD_CONFIG_DIR, "*" + LUA_EXTENSION)) {
            for (Path file : files) {
                String profileName = stripExtension(file.getFileName().toString());
                if (!ConfigManager.hasProfile(profileName)) ConfigManager.addProfile(profileName);
            }
        } catch (IOException e) {
            ErrorManager.reportSystem("profile sync", e);
        }

        for (ConfigData.ProfileData profile : ConfigManager.getProfiles()) {
            String profileName = profile.getName();
            Path expectedFile = ConfigManager.MOD_CONFIG_DIR.resolve(profileName + LUA_EXTENSION);
            if (Files.notExists(expectedFile)) {
                ConfigManager.removeProfile(profileName);
            }
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

                    if (!fileName.endsWith(LUA_EXTENSION)) continue;
                    String profileName = stripExtension(fileName);

                    // Rename/delete rollbacks can leave stale events in the queue.
                    // Wait for the config transaction, then check the current file state.
                    synchronized (ConfigManager.class) {
                        Path profileFile = ConfigManager.MOD_CONFIG_DIR.resolve(changedFile);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (Files.exists(profileFile)) continue;
                            ConfigManager.removeProfile(profileName);
                            reloadNeeded.set(true);
                        } else if (Files.isRegularFile(profileFile)) {
                            if (!ConfigManager.hasProfile(profileName)) {
                                ConfigManager.addProfile(profileName);
                            }
                            reloadNeeded.set(true);
                        }
                    }
                }
                boolean valid = key.reset();

                if (!valid) break;
            }
        } catch (ClosedWatchServiceException e) {
            // Here will trigger when the watcher is closed. No problem, just skip
        } catch (Exception e) {
            ErrorManager.reportSystem("file watcher", e);
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.substring(0, fileName.length() - LUA_EXTENSION.length());
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
