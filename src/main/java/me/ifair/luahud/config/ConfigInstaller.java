package me.ifair.luahud.config;

import me.ifair.luahud.LuaHUD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static me.ifair.luahud.config.ConfigManager.*;

public class ConfigInstaller {
    public static void generateDefaults() {
        final String defaultSource = "/assets/luahud/default-config/";

        if (Files.notExists(MOD_CONFIG_DIR) || !Files.isDirectory(MOD_CONFIG_DIR)) {
            try {
                Files.createDirectory(MOD_CONFIG_DIR);

            } catch (IOException e) {
                LuaHUD.LOGGER.error("There was an error while generating default config directory.");
            }
        }

        if (ConfigManager.isGenerateExampleProfilesEnabled()) {
            copyFromResource(defaultSource + "profiles/Example Profile.lua", MOD_CONFIG_DIR);
            // other default profiles will come here

            ConfigManager.setGenerateExampleProfiles(false);
        }

        // Generate lua files no matter what
        final Path luaDir = MOD_CONFIG_DIR.resolve("api");
        try {
            Files.createDirectories(luaDir);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while creating luahud/api directory: {}", e.getMessage());
        }
        copyFromResource(defaultSource + "lua/.luarc.json", luaDir);
        copyFromResource(defaultSource + "lua/lh.lua", luaDir);
    }

    private static void copyFromResource(String resourcePath, Path targetDir) {
        try (InputStream stream = ConfigInstaller.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IllegalStateException("Resource not found: " + resourcePath); // If it's null, then me (the developer) made the mistake so I should fail-fast

            String fileName = resourcePath.contains("/")
                    ? resourcePath.substring(resourcePath.lastIndexOf("/") + 1)
                    : resourcePath;

            Path targetFile = targetDir.resolve(fileName);

            Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("There was an error while generating files. source: '{}' destination: '{}' error: {}", resourcePath, targetDir.toString(), e.getMessage());
        }
    }
}
