package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static void load() {
        if (!Files.exists(JSON_CONFIG)) {
            ConfigInstaller.generateDefaults();
        }

        try (BufferedReader reader = Files.newBufferedReader(JSON_CONFIG)) {
            config = gson.fromJson(reader, ConfigData.class);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while reading the config file: {}", e.getMessage());
        }
    }

    public static void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(JSON_CONFIG)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while saving config to disk: {}", e.getMessage());
        }

    }




    public static boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static Map<String, ConfigData.ProfileData> getProfiles() { return Collections.unmodifiableMap(config.profiles); }
    public static void setShowHiddenFiles(boolean value) { config.showHiddenFiles = value; }
    public static void setGenerateExampleProfiles(boolean value) { config.generateExampleProfiles = value; }
    public static void addProfile(String name, ConfigData.ProfileData profile) { config.profiles.put(name, profile); }
    public static void addProfile(String name) { config.profiles.put(name, new ConfigData.ProfileData()); }
    public static boolean removeProfile(String name) { return config.profiles.remove(name) != null; }
}
