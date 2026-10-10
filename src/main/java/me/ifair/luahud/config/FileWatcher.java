package me.ifair.luahud.config;

import me.ifair.luahud.error.ErrorManager;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileWatcher {
    private static final String LUA_EXTENSION = ".lua";
    private static final long SAVE_QUIET_PERIOD_MS = 150;

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

            watchService = FileSystems.getDefault().newWatchService();
            ConfigManager.MOD_CONFIG_DIR.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            syncExistingFiles();

            running = true;
            WatchService service = watchService;
            watchThread = new Thread(() -> runWatcher(service), "LuaHUD-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

        } catch (IOException e) {
            stop();
            ErrorManager.reportSystem("file watcher", e);
        }
    }

    // Make sure every .lua file in the MOD_CONFIG_DIR matches someone in the luahud.json
    private void syncExistingFiles() {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(ConfigManager.MOD_CONFIG_DIR, "*" + LUA_EXTENSION)) {
            for (Path file : files) {
                if (!Files.isRegularFile(file)) continue;
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

    private void runWatcher(WatchService service) {
        Set<Path> changedFiles = new LinkedHashSet<>();
        boolean overflow = false;
        try {
            WatchKey key;
            while (running) {
                key = changedFiles.isEmpty() && !overflow
                        ? service.take() : service.poll(SAVE_QUIET_PERIOD_MS, TimeUnit.MILLISECONDS);
                if (key == null) {
                    synchronized (ConfigManager.class) {
                        if (overflow) syncExistingFiles();
                        for (Path changedFile : changedFiles) reconcileFile(changedFile);
                    }
                    reloadNeeded.set(true);
                    changedFiles.clear();
                    overflow = false;
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        overflow = true;
                        continue;
                    }

                    Path changedFile = (Path) event.context();
                    String fileName = changedFile.toString();

                    if (!fileName.endsWith(LUA_EXTENSION)) continue;
                    changedFiles.add(changedFile);
                }
                boolean valid = key.reset();

                if (!valid) break;
            }
        } catch (ClosedWatchServiceException e) {
            // Here will trigger when the watcher is closed. No problem, just skip
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ErrorManager.reportSystem("file watcher", e);
        }
    }

    private void reconcileFile(Path changedFile) {
        // Editors may delete and recreate a script during one save. Reconcile
        // only after the event burst settles, preserving its profile settings.
        String profileName = stripExtension(changedFile.toString());
        Path profileFile = ConfigManager.MOD_CONFIG_DIR.resolve(changedFile);
        if (Files.notExists(profileFile)) {
            ConfigManager.removeProfile(profileName);
        } else if (Files.isRegularFile(profileFile) && !ConfigManager.hasProfile(profileName)) {
            ConfigManager.addProfile(profileName);
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
