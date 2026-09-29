package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import me.ifair.luahud.error.ErrorManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
            ConfigInstaller.generateDefaults();
        }

        try (BufferedReader reader = Files.newBufferedReader(JSON_CONFIG)) {
            JsonElement document = JsonParser.parseReader(reader);
            ConfigData loaded = gson.fromJson(document, ConfigData.class);
            config = (loaded != null) ? loaded : new ConfigData();
            if (config.profiles == null) config.profiles = new ConfigData().profiles;
            config.profiles.removeIf(profile -> profile == null
                    || profile.getName() == null
                    || profile.getName().isBlank());
            saveNormalizedConfig = document == null
                    || !document.isJsonObject()
                    || !document.getAsJsonObject().has("luaTimeoutMs");
            saveNormalizedConfig |= config.normalize();
            config.profiles.forEach(ConfigData.ProfileData::normalize);

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
        try (BufferedWriter writer = Files.newBufferedWriter(JSON_CONFIG)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            ErrorManager.reportSystem("config write", e);
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

        config.showHiddenFiles = value;
        save();
        profileRevision++;
    }

    public static synchronized void setGenerateExampleProfiles(boolean value) {
        if (config.generateExampleProfiles == value) return;

        config.generateExampleProfiles = value;
        save();
    }

    public static synchronized boolean hasProfile(String name) {
        return findProfile(name) != null;
    }

    public static synchronized void addProfile(String name, ConfigData.ProfileData profile) {
        while (ConfigManager.hasProfile(name)) {
            name = incrementSuffix(name);
        }
        config.profiles.add(profile);
        save();
        profileReloadNeeded.set(true);
        profileRevision++;
        profileKeybindRevision++;
    }

    private static String incrementSuffix(String name) {
        Pattern pattern = Pattern.compile("^(.*)\\((\\d+)\\)$");
        Matcher matcher = pattern.matcher(name);

        if (matcher.matches()) {
            String prefix = matcher.group(1);
            int number = Integer.parseInt(matcher.group(2));
            return prefix + "(" + (number + 1) + ")";
        } else {
            return name + " (1)";
        }
    }

    public static synchronized void addProfile(String name) {
        addProfile(name, new ConfigData.ProfileData(name));
    }

    public static synchronized void removeProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;
        config.profiles.remove(index);

        save();
        profileReloadNeeded.set(true);
        profileRevision++;
        profileKeybindRevision++;
    }

    public static synchronized void deleteProfile(String name) {
        int index = indexOfProfile(name);
        if (index < 0) return;

        Path profileFile = MOD_CONFIG_DIR.resolve(name + ".lua").normalize();

        config.profiles.remove(index);
        save();

        try {
            Files.deleteIfExists(profileFile);
            profileReloadNeeded.set(true);
            profileRevision++;
            profileKeybindRevision++;
        } catch (IOException e) {
            save();
            ErrorManager.reportSystem("profile delete", e);
        }
    }

    public static synchronized void moveProfile(String name, int offset) {
        int index = indexOfProfile(name);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= config.profiles.size()) return;

        Collections.swap(config.profiles, index, target);
        save();
        profileReloadNeeded.set(true);
        profileRevision++;
    }

    public static synchronized void setProfileActive(String name, boolean active) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null || profile.isActive() == active) return;

        profile.setActive(active);
        save();
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

        profile.setHidden(hidden);
        save();
        profileRevision++;
    }

    public static synchronized void setProfileKeybind(String name, String keybind) {
        ConfigData.ProfileData profile = findProfile(name);
        if (profile == null) return;
        if (profile.getKeybind().equals(keybind)) return;

        profile.setKeybind(keybind);
        save();
        profileRevision++;
        profileKeybindRevision++;
    }

    public static synchronized void renameProfile(String oldName, String newName) {
        if (oldName.equals(newName)) return;

        ConfigData.ProfileData profile = findProfile(oldName);
        if (profile == null) return;

        while (hasProfile(newName)) {
            newName = incrementSuffix(newName);
        }

        Path oldFile = MOD_CONFIG_DIR.resolve(oldName + ".lua").normalize();
        Path newFile = MOD_CONFIG_DIR.resolve(newName + ".lua").normalize();

        profile.setName(newName);

        try {
            Files.move(oldFile, newFile);
            save();
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

    private static int indexOfProfile(String name) {
        for (int index = 0; index < config.profiles.size(); index++) {
            if (name.equals(config.profiles.get(index).getName())) return index;
        }
        return -1;
    }
}
