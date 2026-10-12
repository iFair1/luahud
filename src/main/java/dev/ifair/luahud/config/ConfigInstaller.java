package dev.ifair.luahud.config;

import dev.ifair.luahud.error.ErrorManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.ifair.luahud.config.ConfigManager.*;

public class ConfigInstaller {
    public static void generateDefaults() {
        final String source = "/assets/luahud/";

        if (Files.notExists(MOD_CONFIG_DIR) || !Files.isDirectory(MOD_CONFIG_DIR)) {
            try {
                Files.createDirectory(MOD_CONFIG_DIR);

            } catch (IOException e) {
                ErrorManager.reportSystem("default config", e);
            }
        }

        if (ConfigManager.isGenerateExampleProfilesEnabled()) {
            final String profileDir = source + "lua/";
            copyFromResourceIfAbsent(profileDir + "Welcome Profile.lua", MOD_CONFIG_DIR);
            if (Files.isRegularFile(MOD_CONFIG_DIR.resolve("Welcome Profile.lua"))
                    && !ConfigManager.hasProfile("Welcome Profile")) {
                ConfigData.ProfileData welcome = new ConfigData.ProfileData("Welcome Profile");
                welcome.setKeybind("key.keyboard.j");
                ConfigManager.addProfile(welcome.getName(), welcome);
            }
            // other default profiles will come here

            ConfigManager.setGenerateExampleProfiles(false);
        }

        // The API stub is generated from the frozen registry. Only install LuaLS settings here.
        final Path luaDir = MOD_CONFIG_DIR.resolve("api");
        try {
            Files.createDirectories(luaDir);
        } catch (IOException e) {
            ErrorManager.reportSystem("default config", e);
        }
        copyFromResourceIfAbsent(source + "lua/.luarc.json", luaDir);
    }

    static void copyFromResourceIfAbsent(String resourcePath, Path targetDir) {
        try (InputStream stream = ConfigInstaller.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IllegalStateException("Resource not found: " + resourcePath); // If it's null, then me (the developer) made the mistake, so I should fail-fast

            String fileName = resourcePath.contains("/")
                    ? resourcePath.substring(resourcePath.lastIndexOf("/") + 1)
                    : resourcePath;

            Path targetFile = targetDir.resolve(fileName);

            Files.copy(stream, targetFile);
        } catch (FileAlreadyExistsException ignored) {
            // Preserve user changes to files installed on an earlier run.
        } catch (IOException e) {
            ErrorManager.reportSystem("default config", e);
        }
    }
}
