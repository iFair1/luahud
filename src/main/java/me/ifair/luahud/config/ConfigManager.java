package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.ifair.luahud.LuaHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud/");
    private static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    public static class ConfigData {
        public boolean showHiddenFiles;
        public Map<String, ProfileData> profiles = new HashMap<>();
        public boolean generateExampleProfiles;

        public static class ProfileData {
            public boolean active = false;
            public String keybind = "key.keyboard.unknown";
        }
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigData config = new ConfigData();

    public static boolean isShowHiddenFilesEnabled() { return config.showHiddenFiles; }
    public static boolean isGenerateExampleProfilesEnabled() { return config.generateExampleProfiles; }
    public static Map<String, ConfigData.ProfileData> getProfiles() { return Collections.unmodifiableMap(config.profiles); }



    public static void load() {
        if (!Files.exists(JSON_CONFIG)) {
            generateDefaults();
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

    // Generate defaults if they don't exist
    public static void generateDefaults() {
        final String defaultSource = "/assets/luahud/default-config/";

        if (Files.notExists(JSON_CONFIG)) {
            FileHelper.copyFromResource(defaultSource + "luahud.json", CONFIG_DIR);
        }

        if (Files.notExists(MOD_CONFIG_DIR) || !Files.isDirectory(MOD_CONFIG_DIR)) {
            try {
                Files.createDirectory(MOD_CONFIG_DIR);

            } catch (IOException e) {
                LuaHUD.LOGGER.error("There was an error while generating default config directory.");
            }
        }

        if (ConfigManager.isGenerateExampleProfilesEnabled()) {
            FileHelper.copyFromResource(defaultSource + "luahud/Example Profile.lua", MOD_CONFIG_DIR);
            // other default profiles will come here
        }

        // Generate lua files no matter what
        final Path luaDir = MOD_CONFIG_DIR.resolve("api/");
        FileHelper.copyFromResource(defaultSource + "luahud/api/.luarc.json", luaDir);
        FileHelper.copyFromResource(defaultSource + "luahud/api/lh.lua", luaDir);

        save();
    }

}
