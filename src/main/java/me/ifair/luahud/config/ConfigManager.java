package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.error.ErrorManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigManager {
    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud");
    static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean profileReloadNeeded = new AtomicBoolean(false);
    private static ConfigData config = new ConfigData();
    private static long profileKeybindRevision;

    public static synchronized void load() {
        if (Files.notExists(JSON_CONFIG)) {
            ConfigInstaller.generateDefaults();
        }

        try (BufferedReader reader = Files.newBufferedReader(JSON_CONFIG)) {
            ConfigData loaded = gson.fromJson(reader, ConfigData.class);
            config = (loaded != null) ? loaded : new ConfigData();
            if (config.profiles == null) config.profiles = new ConfigData().profiles;
            config.profiles.removeIf(profile -> profile == null
                    || profile.getName() == null
                    || profile.getName().isBlank());
            config.profiles.forEach(ConfigData.ProfileData::normalize);

        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while reading the config file: {}", e.getMessage());
            ErrorManager.reportSystem("config read", e);
        } catch (JsonSyntaxException e) {
            LuaHUD.LOGGER.error("Config JSON is invalid. Loading defaults: {}", e.getMessage());
            ErrorManager.reportSystem("config read", e);
            config = new ConfigData();
        }
        profileKeybindRevision++;
    }

    private static boolean save() {
        try (BufferedWriter writer = Files.newBufferedWriter(JSON_CONFIG)) {
            gson.toJson(config, writer);
            return true;
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while saving config to disk: {}", e.getMessage());
            ErrorManager.reportSystem("config write", e);
            return false;
        }
    }

    // This is where the profiles live
    public static Path getScriptsDir() {
        return MOD_CONFIG_DIR;
    }


    public static synchronized boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static synchronized boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static synchronized List<ConfigData.ProfileData> getProfiles() {
        List<ConfigData.ProfileData> snapshot = new ArrayList<>();
        config.profiles.forEach(profile -> snapshot.add(profile.copy()));
        return Collections.unmodifiableList(snapshot);
    }

    public static synchronized boolean setShowHiddenFiles(boolean value) {
        if (config.showHiddenFiles == value) return true;

        boolean previous = config.showHiddenFiles;
        config.showHiddenFiles = value;
        if (save()) return true;

        config.showHiddenFiles = previous;
        return false;
    }

    public static synchronized boolean setGenerateExampleProfiles(boolean value) {
        if (config.generateExampleProfiles == value) return true;

        boolean previous = config.generateExampleProfiles;
        config.generateExampleProfiles = value;
        if (save()) return true;

        config.generateExampleProfiles = previous;
        return false;
    }

    public static synchronized boolean hasProfile(String name) {
        return findProfile(name) != null;
    }

    public static synchronized boolean addProfile(String name, ConfigData.ProfileData profile) {
        if (name == null || name.isBlank() || profile == null) return false;

        int index = indexOfProfile(name);
        ConfigData.ProfileData replacement = profile.copy();
        replacement.setName(name);
        ConfigData.ProfileData previous = index >= 0
                ? config.profiles.set(index, replacement)
                : null;
        if (index < 0) config.profiles.add(replacement);
        if (save()) {
            profileReloadNeeded.set(true);
            profileKeybindRevision++;
            return true;
        }

        restoreProfile(index, previous);
        return false;
    }

    public static synchronized boolean addProfile(String name) {
        return addProfile(name, new ConfigData.ProfileData(name));
    }

    public static synchronized boolean removeProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return false;
        ConfigData.ProfileData removed = config.profiles.remove(index);

        if (save()) {
            profileReloadNeeded.set(true);
            profileKeybindRevision++;
            return true;
        }

        config.profiles.add(index, removed);
        return false;
    }

    public static synchronized boolean setProfileActive(String name, boolean active) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return false;
        if (profile.isActive() == active) return true;

        boolean previous = profile.isActive();
        profile.setActive(active);
        if (save()) {
            profileReloadNeeded.set(true);
            return true;
        }

        profile.setActive(previous);
        return false;
    }

    public static synchronized boolean toggleProfileActive(String name) {
        ConfigData.ProfileData profile = findProfile(name);
        return profile != null && setProfileActive(name, !profile.isActive());
    }

    public static synchronized boolean setProfileHidden(String name, boolean hidden) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return false;
        if (profile.isHidden() == hidden) return true;

        boolean previous = profile.isHidden();
        profile.setHidden(hidden);
        if (save()) return true;

        profile.setHidden(previous);
        return false;
    }

    public static synchronized boolean setProfileKeybind(String name, String keybind) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || keybind == null || keybind.isBlank()) return false;
        if (profile.getKeybind().equals(keybind)) return true;

        String previous = profile.getKeybind();
        profile.setKeybind(keybind);
        if (save()) {
            profileKeybindRevision++;
            return true;
        }

        profile.setKeybind(previous);
        return false;
    }

    public static synchronized boolean renameProfile(String oldName, String newName) {
        String trimmedName = newName.trim();
        if (oldName.equals(trimmedName)) return true;
        ConfigData.ProfileData profile = findProfile(oldName);
        if (trimmedName.isEmpty() || profile == null) return false;

        Path oldFile = MOD_CONFIG_DIR.resolve(oldName + ".lua").normalize();
        Path newFile = MOD_CONFIG_DIR.resolve(trimmedName + ".lua").normalize();
        if (!MOD_CONFIG_DIR.equals(newFile.getParent())
                || hasProfile(trimmedName)
                || Files.exists(newFile)) {
            return false;
        }

        profile.setName(trimmedName);

        try {
            Files.move(oldFile, newFile);
            if (save()) {
                profileReloadNeeded.set(true);
                profileKeybindRevision++;
                return true;
            }

            profile.setName(oldName);
            try {
                Files.move(newFile, oldFile);
            } catch (IOException rollbackError) {
                LuaHUD.LOGGER.error("Could not roll back profile file rename from '{}' to '{}': {}", trimmedName, oldName, rollbackError.getMessage());
                ErrorManager.reportSystem("profile rename rollback", rollbackError);
            }
            return false;
        } catch (IOException e) {
            profile.setName(oldName);
            LuaHUD.LOGGER.error("There was an error while renaming profile '{}' to '{}': {}", oldName, trimmedName, e.getMessage());
            ErrorManager.reportSystem("profile rename", e);
            return false;
        }
    }

    public static boolean consumeProfileReloadSignal() {
        return profileReloadNeeded.compareAndSet(true, false);
    }

    public static synchronized long getProfileKeybindRevision() {
        return profileKeybindRevision;
    }

    private static ConfigData.ProfileData findProfile(String name) {
        int index = indexOfProfile(name);
        return index >= 0 ? config.profiles.get(index) : null;
    }

    private static int indexOfProfile(String name) {
        if (name == null) return -1;
        for (int index = 0; index < config.profiles.size(); index++) {
            if (name.equals(config.profiles.get(index).getName())) return index;
        }
        return -1;
    }

    private static void restoreProfile(int index, ConfigData.ProfileData profile) {
        if (profile == null) {
            config.profiles.removeLast();
        } else {
            config.profiles.set(index, profile);
        }
    }
}
