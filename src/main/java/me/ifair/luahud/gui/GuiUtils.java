package me.ifair.luahud.gui;

import me.ifair.luahud.config.ConfigManager;
import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Path;

public class GuiUtils {
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    public static void openInBrowser(String url) {
        // No need to add a try-catch because the links are always provided by me. "And I do no mistakes" - iFair
        Util.getOperatingSystem().open(new URI(url));
    }
}
