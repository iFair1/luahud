package dev.ifair.luahud.config;

import dev.ifair.luahud.error.ErrorManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.ifair.luahud.config.ConfigManager.*;

public class ConfigInstaller {
    private static final String ASSETS_SOURCE = "/assets/luahud/";
    private static final String PROFILES_SOURCE = ASSETS_SOURCE + "lua/";

    public static void generateDefaults() {

        if (Files.notExists(MOD_CONFIG_DIR) || !Files.isDirectory(MOD_CONFIG_DIR)) {
            try {
                Files.createDirectory(MOD_CONFIG_DIR);

            } catch (IOException e) {
                ErrorManager.reportSystem("default config", e);
            }
        }

        if (ConfigManager.isGenerateExampleProfilesEnabled()) {
            generateExampleProfile("Welcome Profile", true, false, "key.keyboard.j");
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
        copyFromResourceIfAbsent(ASSETS_SOURCE + "lua/.luarc.json", luaDir);
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

    private static void generateExampleProfile(String name, boolean active, boolean hidden) {
        generateExampleProfile(name, active, hidden, "key.keyboard.unknown");
    }

    private static void generateExampleProfile(String name, boolean active, boolean hidden, String keybind) {
        String nameWithExtension = name + ".lua";
        copyFromResourceIfAbsent(PROFILES_SOURCE + nameWithExtension, MOD_CONFIG_DIR);
        if (Files.isRegularFile(MOD_CONFIG_DIR.resolve(nameWithExtension))
        && !ConfigManager.hasProfile(name)) {
            ConfigData.ProfileData newProfile = new ConfigData.ProfileData(name);
            newProfile.setActive(active);
            newProfile.setHidden(hidden);
            newProfile.setKeybind(keybind);

            ConfigManager.addProfile(name, newProfile);
        }
    }
}
