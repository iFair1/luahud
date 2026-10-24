package dev.ifair.luahud.gui;

import dev.ifair.luahud.LuaHUD;
import dev.ifair.luahud.config.ConfigManager;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class GuiUtils {
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    public static int openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
            return 1;
        } catch (URISyntaxException e) {
            LuaHUD.LOGGER.error("Error while opening \"{}\" in browser: {}", url, e.getMessage());
            return 0;
        }
    }
}
