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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud");
    static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean profileReloadNeeded = new AtomicBoolean(false);
    private static ConfigData config = new ConfigData();
    private static long profileRevision;
    private static long profileKeybindRevision;

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
                    || !document.getAsJsonObject().has("luaTimeoutMs");
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

    private static boolean commit(ConfigData candidate) {
        if (!persist(candidate)) return false;
        config = candidate;
        return true;
    }

    private static ConfigData copyConfig() {
        ConfigData copy = new ConfigData();
        copy.showHiddenFiles = config.showHiddenFiles;
        copy.generateExampleProfiles = config.generateExampleProfiles;
        copy.luaTimeoutMs = config.luaTimeoutMs;
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

    // This is where the profiles live
    public static Path getScriptsDir() {
        return MOD_CONFIG_DIR;
    }


    public static synchronized boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static synchronized boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static synchronized int getLuaTimeoutMs() { return config.luaTimeoutMs; }
    public static synchronized List<ConfigData.ProfileData> getProfiles() {
        List<ConfigData.ProfileData> snapshot = new ArrayList<>();
        config.profiles.forEach(profile -> snapshot.add(profile.copy()));
        return Collections.unmodifiableList(snapshot);
    }

    public static synchronized void setShowHiddenFiles(boolean value) {
        if (config.showHiddenFiles == value) return;

        ConfigData candidate = copyConfig();
        candidate.showHiddenFiles = value;
        if (!commit(candidate)) return;
        profileRevision++;
    }

    public static synchronized void setGenerateExampleProfiles(boolean value) {
        if (config.generateExampleProfiles == value) return;

        ConfigData candidate = copyConfig();
        candidate.generateExampleProfiles = value;
        commit(candidate);
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
        if (!commit(candidate)) return;
        profileReloadNeeded.set(true);
        profileRevision++;
        profileKeybindRevision++;
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
        Path profileFile = MOD_CONFIG_DIR.resolve(name + ".lua").normalize();
        while (hasProfile(name) || Files.exists(profileFile)) {
            name = incrementSuffix(name);
            profileFile = MOD_CONFIG_DIR.resolve(name + ".lua").normalize();
        }

        ConfigData candidate = copyConfig();
        candidate.profiles.add(new ConfigData.ProfileData(name));

        try {
            Files.createFile(profileFile);
            if (!commit(candidate)) {
                Files.deleteIfExists(profileFile);
                return;
            }
            profileReloadNeeded.set(true);
            profileRevision++;
            profileKeybindRevision++;
        } catch (IOException e) {
            ErrorManager.reportSystem("profile create", e);
        }
    }

    public static synchronized void removeProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;
        ConfigData candidate = copyConfig();
        candidate.profiles.remove(index);
        if (!commit(candidate)) return;
        profileReloadNeeded.set(true);
        profileRevision++;
        profileKeybindRevision++;
    }

    public static synchronized void deleteProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;

        Path profileFile = MOD_CONFIG_DIR.resolve(name + ".lua").normalize();
        ConfigData candidate = copyConfig();
        candidate.profiles.remove(index);
        Path stagedFile = null;
        boolean deleteStagedFile = true;
        try {
            if (Files.exists(profileFile)) {
                if (!Files.isRegularFile(profileFile)) {
                    throw new IOException("Profile is not a regular file: " + profileFile);
                }
                stagedFile = Files.createTempFile(MOD_CONFIG_DIR, "luahud-delete-", ".tmp");
                Files.move(profileFile, stagedFile, StandardCopyOption.REPLACE_EXISTING);
                deleteStagedFile = false;
            }
            if (!commit(candidate)) {
                if (stagedFile != null) restoreFile(stagedFile, profileFile, "profile delete");
                return;
            }
            deleteStagedFile = true;
            profileReloadNeeded.set(true);
            profileRevision++;
            profileKeybindRevision++;
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

        ConfigData candidate = copyConfig();
        Collections.swap(candidate.profiles, index, target);
        if (!commit(candidate)) return;
        profileReloadNeeded.set(true);
        profileRevision++;
    }

    public static synchronized void setProfileActive(String name, boolean active) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || profile.isActive() == active) return;

        ConfigData candidate = copyConfig();
        candidate.profiles.get(indexOfProfile(name)).setActive(active);
        if (!commit(candidate)) return;
        profileReloadNeeded.set(true);
        profileRevision++;
    }

    public static synchronized void toggleProfileActive(String name) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return;
        setProfileActive(name, !profile.isActive());
    }

    public static synchronized void setProfileHidden(String name, boolean hidden) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || profile.isHidden() == hidden) return;

        ConfigData candidate = copyConfig();
        candidate.profiles.get(indexOfProfile(name)).setHidden(hidden);
        if (!commit(candidate)) return;
        profileRevision++;
    }

    public static synchronized void setProfileKeybind(String name, String keybind) {
        keybind = ConfigData.ProfileData.normalizeKeybind(keybind);
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return;
        if (profile.getKeybind().equals(keybind)) return;

        ConfigData candidate = copyConfig();
        candidate.profiles.get(indexOfProfile(name)).setKeybind(keybind);
        if (!commit(candidate)) return;
        profileRevision++;
        profileKeybindRevision++;
    }

    public static synchronized void renameProfile(String oldName, String newName) {
        if (!validateProfileName(newName)) return;
        if (oldName.equals(newName)) return;

        ConfigData.ProfileData profile = findProfile(oldName);
        if (profile == null) return;

        while (hasProfile(newName)) {
            newName = incrementSuffix(newName);
        }

        Path oldFile = MOD_CONFIG_DIR.resolve(oldName + ".lua").normalize();
        Path newFile = MOD_CONFIG_DIR.resolve(newName + ".lua").normalize();

        ConfigData candidate = copyConfig();
        candidate.profiles.get(indexOfProfile(oldName)).setName(newName);

        try {
            Files.move(oldFile, newFile);
            if (!commit(candidate)) {
                restoreFile(newFile, oldFile, "profile rename");
                return;
            }
            profileReloadNeeded.set(true);
            profileRevision++;
            profileKeybindRevision++;
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
