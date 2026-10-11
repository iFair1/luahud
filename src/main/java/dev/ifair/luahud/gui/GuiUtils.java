package dev.ifair.luahud.gui;

import dev.ifair.luahud.LuaHUD;
import dev.ifair.luahud.config.ConfigManager;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class GuiUtils {
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    public static void openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (URISyntaxException e) {
            LuaHUD.LOGGER.error("Error while opening \"{}\" in browser: {}", url, e.getMessage());
        }
    }
}
