package me.ifair.luahud.config;

import me.ifair.luahud.LuaHUD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class FileHelper {
    public static void copyFromResource(String resourcePath, Path targetDir) {
        try (InputStream stream = FileHelper.class.getResourceAsStream(resourcePath)) {
            assert stream != null; // If it's null, then me (the developer) made the mistake

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
