package dev.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.ifair.luahud.error.ErrorManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud");
    static final Path SYSTEM_DIR = MOD_CONFIG_DIR.resolve("system");
    static final Path PROFILES_DIR = MOD_CONFIG_DIR.resolve("profiles");
    static final Path API_DIR = MOD_CONFIG_DIR.resolve("api");
    static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean profileReloadNeeded = new AtomicBoolean(false);
    private static ConfigData config = new ConfigData();
    private static long profileRevision;
    private static long profileKeybindRevision;

    private enum Change { PROFILES, KEYBINDS, RELOAD }

    public static synchronized void load() {
        boolean saveNormalizedConfig = false;
        if (Files.notExists(JSON_CONFIG)) {
            config = new ConfigData();
            save();
            profileRevision++;
            profileKeybindRevision++;
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(JSON_CONFIG)) {
            JsonElement document = JsonParser.parseReader(reader);
            ConfigData loaded = gson.fromJson(document, ConfigData.class);
            config = (loaded != null) ? loaded : new ConfigData();
            if (config.profiles == null) config.profiles = new ConfigData().profiles;
            boolean removedProfiles = config.profiles.removeIf(profile -> profile == null
                    || !ConfigData.isValidProfileName(profile.getName()));
            saveNormalizedConfig = document == null
                    || !document.isJsonObject()
                    || !document.getAsJsonObject().has("luaTimeoutMs")
                    || !document.getAsJsonObject().has("sectionDurationClock");
            saveNormalizedConfig |= config.normalize();
            saveNormalizedConfig |= removedProfiles;
            for (ConfigData.ProfileData profile : config.profiles) {
                saveNormalizedConfig |= profile.normalize();
            }

        } catch (IOException e) {
            ErrorManager.reportSystem("config read", e);
        } catch (JsonSyntaxException e) {
            ErrorManager.reportSystem("config read", e);
            config = new ConfigData();
        }
        if (saveNormalizedConfig) save();
        profileRevision++;
        profileKeybindRevision++;
    }

    private static void save() {
        persist(config);
    }

    private static boolean persist(ConfigData candidate) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(CONFIG_DIR, "luahud-", ".json.tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile)) {
                gson.toJson(candidate, writer);
            }
            try {
                Files.move(temporaryFile, JSON_CONFIG,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, JSON_CONFIG, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            ErrorManager.reportSystem("config write", e);
            return false;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    ErrorManager.reportSystem("config temporary file cleanup", e);
                }
            }
        }
    }

    private static boolean commit(ConfigData candidate, Change... changes) {
        if (!persist(candidate)) return false;
        config = candidate;
        for (Change change : changes) {
            switch (change) {
                case PROFILES -> profileRevision++;
                case KEYBINDS -> profileKeybindRevision++;
                case RELOAD -> profileReloadNeeded.set(true);
            }
        }
        return true;
    }

    private static void updateConfig(Consumer<ConfigData> update, Change... changes) {
        ConfigData candidate = copyConfig();
        update.accept(candidate);
        commit(candidate, changes);
    }

    private static ConfigData copyConfig() {
        ConfigData copy = new ConfigData();
        copy.showHiddenFiles = config.showHiddenFiles;
        copy.generateExampleProfiles = config.generateExampleProfiles;
        copy.luaTimeoutMs = config.luaTimeoutMs;
        copy.sectionDurationClock = config.sectionDurationClock;
        config.profiles.forEach(profile -> copy.profiles.add(profile.copy()));
        return copy;
    }

    private static void restoreFile(Path source, Path target, String component) {
        try {
            Files.move(source, target);
        } catch (IOException e) {
            ErrorManager.reportSystem(component + " rollback", e);
        }
    }

    /** System scripts have runtime owners, but no configurable profile entries. */
    public static java.util.List<String> getSystemScripts() {
        try (var files = Files.list(SYSTEM_DIR)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".lua"))
                    .sorted()
                    .map(name -> "system/" + name.substring(0, name.length() - 4))
                    .toList();
        } catch (IOException error) {
            ErrorManager.reportSystem("system scripts", error);
            return java.util.List.of();
        }
    }

    public static Path getProfilePath(String name) {
        if (name.startsWith("system/")) return SYSTEM_DIR.resolve(name.substring(7) + ".lua").normalize();
        return PROFILES_DIR.resolve(name + ".lua").normalize();
    }

    public static Path getScriptsDir() {
        return PROFILES_DIR;
    }


    public static Path getApiDir() {
        return API_DIR;
    }

    public static synchronized boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static synchronized boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static synchronized int getLuaTimeoutMs() { return config.luaTimeoutMs; }
    public static synchronized String getSectionDurationClock() { return config.sectionDurationClock; }
    public static synchronized List<ConfigData.ProfileData> getProfiles() {
        List<ConfigData.ProfileData> snapshot = new ArrayList<>();
        config.profiles.forEach(profile -> snapshot.add(profile.copy()));
        return Collections.unmodifiableList(snapshot);
    }

    public static synchronized void setShowHiddenFiles(boolean value) {
        if (config.showHiddenFiles == value) return;

        updateConfig(candidate -> candidate.showHiddenFiles = value, Change.PROFILES);
    }

    public static synchronized void setGenerateExampleProfiles(boolean value) {
        if (config.generateExampleProfiles == value) return;

        updateConfig(candidate -> candidate.generateExampleProfiles = value);
    }

    public static synchronized boolean hasProfile(String name) {
        return findProfile(name) != null;
    }

    public static synchronized void addProfile(String name, ConfigData.ProfileData profile) {
        if (!validateProfileName(name)) return;
        while (ConfigManager.hasProfile(name)) {
            name = incrementSuffix(name);
        }
        ConfigData candidate = copyConfig();
        ConfigData.ProfileData added = profile.copy();
        added.setName(name);
        added.normalize();
        candidate.profiles.add(added);
        commit(candidate, Change.RELOAD, Change.PROFILES, Change.KEYBINDS);
    }

    private static String incrementSuffix(String name) {
        Pattern pattern = Pattern.compile("^(.*)\\((\\d+)\\)$");
        Matcher matcher = pattern.matcher(name);

        if (matcher.matches()) {
            String prefix = matcher.group(1);
            BigInteger number = new BigInteger(matcher.group(2));
            return prefix + "(" + number.add(BigInteger.ONE) + ")";
        } else {
            return name + " (1)";
        }
    }

    public static synchronized void addProfile(String name) {
        addProfile(name, new ConfigData.ProfileData(name));
    }

    public static synchronized void createProfile(String name) {
        if (!validateProfileName(name)) return;
        Path profileFile = getProfilePath(name);
        while (hasProfile(name) || Files.exists(profileFile)) {
            name = incrementSuffix(name);
            profileFile = getProfilePath(name);
        }

        ConfigData candidate = copyConfig();
        candidate.profiles.add(new ConfigData.ProfileData(name));

        try {
            Files.createFile(profileFile);
            if (!commit(candidate, Change.RELOAD, Change.PROFILES, Change.KEYBINDS)) {
                Files.deleteIfExists(profileFile);
                return;
            }
        } catch (IOException e) {
            ErrorManager.reportSystem("profile create", e);
        }
    }

    public static synchronized void removeProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;
        updateConfig(candidate -> candidate.profiles.remove(index),
                Change.RELOAD, Change.PROFILES, Change.KEYBINDS);
    }

    public static synchronized void deleteProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;

        Path profileFile = getProfilePath(name);
        ConfigData candidate = copyConfig();
        candidate.profiles.remove(index);
        Path stagedFile = null;
        boolean deleteStagedFile = true;
        try {
            if (Files.exists(profileFile)) {
                if (!Files.isRegularFile(profileFile)) {
                    throw new IOException("Profile is not a regular file: " + profileFile);
                }
                stagedFile = Files.createTempFile(PROFILES_DIR, "luahud-delete-", ".tmp");
                Files.move(profileFile, stagedFile, StandardCopyOption.REPLACE_EXISTING);
                deleteStagedFile = false;
            }
            if (!commit(candidate, Change.RELOAD, Change.PROFILES, Change.KEYBINDS)) {
                if (stagedFile != null) restoreFile(stagedFile, profileFile, "profile delete");
                return;
            }
            deleteStagedFile = true;
        } catch (IOException e) {
            ErrorManager.reportSystem("profile delete", e);
        } finally {
            // A failed rollback must retain the staged file for recovery.
            if (stagedFile != null && deleteStagedFile) {
                try {
                    Files.deleteIfExists(stagedFile);
                } catch (IOException e) {
                    ErrorManager.reportSystem("profile delete cleanup", e);
                }
            }
        }
    }

    public static synchronized void moveProfile(String name, int offset) {
        int index = indexOfProfile(name);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= config.profiles.size()) return;

        updateConfig(candidate -> Collections.swap(candidate.profiles, index, target),
                Change.RELOAD, Change.PROFILES);
    }

    public static synchronized void setProfileActive(String name, boolean active) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || profile.isActive() == active) return;

        int index = indexOfProfile(name);
        updateConfig(candidate -> candidate.profiles.get(index).setActive(active),
                Change.RELOAD, Change.PROFILES);
    }

    public static synchronized void toggleProfileActive(String name) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return;
        setProfileActive(name, !profile.isActive());
    }

    public static synchronized void setProfileHidden(String name, boolean hidden) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || profile.isHidden() == hidden) return;

        int index = indexOfProfile(name);
        updateConfig(candidate -> candidate.profiles.get(index).setHidden(hidden), Change.PROFILES);
    }

    public static synchronized void setProfileKeybind(String name, String keybind) {
        String normalized = ConfigData.ProfileData.normalizeKeybind(keybind);
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return;
        if (profile.getKeybind().equals(normalized)) return;

        int index = indexOfProfile(name);
        updateConfig(candidate -> candidate.profiles.get(index).setKeybind(normalized),
                Change.PROFILES, Change.KEYBINDS);
    }

    public static synchronized void renameProfile(String oldName, String newName) {
        if (!validateProfileName(newName)) return;
        if (oldName.equals(newName)) return;

        ConfigData.ProfileData profile = findProfile(oldName);
        if (profile == null) return;

        while (hasProfile(newName)) {
            newName = incrementSuffix(newName);
        }

        Path oldFile = getProfilePath(oldName);
        Path newFile = getProfilePath(newName);

        ConfigData candidate = copyConfig();
        candidate.profiles.get(indexOfProfile(oldName)).setName(newName);

        try {
            Files.move(oldFile, newFile);
            if (!commit(candidate, Change.RELOAD, Change.PROFILES, Change.KEYBINDS)) {
                restoreFile(newFile, oldFile, "profile rename");
                return;
            }
        } catch (IOException e) {
            ErrorManager.reportSystem("profile rename", e);
        }
    }

    public static boolean consumeProfileReloadSignal() {
        return profileReloadNeeded.compareAndSet(true, false);
    }

    public static synchronized long getProfileRevision() {
        return profileRevision;
    }

    public static synchronized long getProfileKeybindRevision() {
        return profileKeybindRevision;
    }

    private static ConfigData.ProfileData findProfile(String name) {
        int index = indexOfProfile(name);
        if (index == -1) return null;

        return config.profiles.get(index);
    }

    private static boolean validateProfileName(String name) {
        if (ConfigData.isValidProfileName(name)) return true;
        ErrorManager.reportSystem("profile name", "Profile name must be a nonblank file name without path separators");
        return false;
    }

    private static int indexOfProfile(String name) {
        for (int index = 0; index < config.profiles.size(); index++) {
            if (name.equals(config.profiles.get(index).getName())) return index;
        }
        return -1;
    }
}
