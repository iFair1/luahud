package me.ifair.luahud.config;

import me.ifair.luahud.LuaHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path MOD_CONFIG_DIR = CONFIG_DIR.resolve("luahud/");
    private static final Path JSON_CONFIG = CONFIG_DIR.resolve("luahud.json");

    // Generate defaults if they don't exist
    public static void generateDefaults() {
        final String defaultSource = "/assets/luahud/default-config/";

        if (!Files.exists(JSON_CONFIG)) {
            FileHelper.copyFromResource(defaultSource + "luahud.json", JSON_CONFIG);
        }

        if (!Files.exists(MOD_CONFIG_DIR) || !Files.isDirectory(MOD_CONFIG_DIR)) {
            try {
                Files.createDirectory(MOD_CONFIG_DIR);

            } catch (IOException e) {
                LuaHUD.LOGGER.error("There was an error while generating default config directory.");
            }
        }

        if (FileHelper.isGenerateExampleProfilesEnabled()) {
            FileHelper.copyFromResource(defaultSource + "luahud/Example Profile.lua", MOD_CONFIG_DIR);
            // other default profiles will come here
        }

        // Generate lua files no matter what
        final Path luaDir = MOD_CONFIG_DIR.resolve("api/");
        FileHelper.copyFromResource(defaultSource + "luahud/api/.luarc.json", luaDir);
        FileHelper.copyFromResource(defaultSource + "/assets/luahud/api/lh.lua", luaDir);
    }

}
