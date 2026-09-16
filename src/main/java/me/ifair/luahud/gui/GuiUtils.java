package me.ifair.luahud.gui;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.error.ErrorManager;
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
