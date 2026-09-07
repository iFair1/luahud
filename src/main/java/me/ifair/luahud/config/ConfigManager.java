package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.ifair.luahud.LuaHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class ConfigManager {
    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud");
    static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigData config = new ConfigData();

    public static synchronized void load() {
        if (Files.notExists(JSON_CONFIG)) {
            ConfigInstaller.generateDefaults();
        }

        try (BufferedReader reader = Files.newBufferedReader(JSON_CONFIG)) {
            ConfigData loaded = gson.fromJson(reader, ConfigData.class);
            config = (loaded != null) ? loaded : new ConfigData();
            if (config.profiles == null) config.profiles = new ConfigData().profiles;

        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while reading the config file: {}", e.getMessage());
        } catch (JsonSyntaxException e) {
            LuaHUD.LOGGER.error("Config JSON is invalid. Loading defaults: {}", e.getMessage());
            config = new ConfigData();
        }
    }

    public static synchronized void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(JSON_CONFIG)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while saving config to disk: {}", e.getMessage());
        }

    }

    // This is where the profiles live
    public static Path getScriptDir() {
        return MOD_CONFIG_DIR;
    }


    public static boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static Map<String, ConfigData.ProfileData> getProfiles() { return Collections.unmodifiableMap(config.profiles); }

    public static void setShowHiddenFiles(boolean value) {
        config.showHiddenFiles = value;
        save();
    }
    public static void setGenerateExampleProfiles(boolean value) {
        config.generateExampleProfiles = value;
        save();
    }
    public static boolean hasProfile(String name) {
        return config.profiles.containsKey(name);
    }
    public static synchronized void addProfile(String name, ConfigData.ProfileData profile) {
        config.profiles.put(name, profile);
        save();
    }
    public static void addProfile(String name) {
        config.profiles.put(name, new ConfigData.ProfileData());
        save();
    }
    public static void removeProfile(String name) {
        config.profiles.remove(name);
        save();
    }

    public static boolean isProfileActive(String name) {
        ConfigData.ProfileData profile = config.profiles.get(name);
        return profile != null && profile.active;
    }
    public static boolean isProfileHidden(String name) {
        ConfigData.ProfileData profile = config.profiles.get(name);
        return profile != null && profile.hidden;
    }
    public static String getProfileKeybind(String name) {
        ConfigData.ProfileData profile = config.profiles.get(name);
        return profile != null ? profile.keybind : "key.keyboard.unknown";
    }
}
