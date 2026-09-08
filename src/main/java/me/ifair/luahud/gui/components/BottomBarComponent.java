package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.config.ConfigManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BottomBarComponent {
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    public static FlowLayout create(Screen parentScreen) {
        FlowLayout bottomBar = Containers.horizontalFlow(Sizing.fill(85), Sizing.content());
        bottomBar.horizontalAlignment(HorizontalAlignment.CENTER);
        bottomBar.verticalAlignment(VerticalAlignment.CENTER);
        bottomBar.margins(Insets.top(6));
        bottomBar.gap(10);

        var openFolderBtn = Components.button(Text.literal("Open Folder"), button -> {
            try {
                if (Files.notExists(PROFILES_DIR)) Files.createDirectories(PROFILES_DIR);
            } catch (IOException e) {
                LuaHUD.LOGGER.error("Error while creating profiles directory at {}: {}", PROFILES_DIR, e.getMessage());
            }
            Util.getOperatingSystem().open(PROFILES_DIR);
        });
        openFolderBtn.sizing(Sizing.fixed(150), Sizing.fixed(20));

        var doneBtn = Components.button(Text.literal("Done"), button -> {
            parentScreen.close();
        });
        doneBtn.sizing(Sizing.fixed(150), Sizing.fixed(20));

        bottomBar.child(openFolderBtn);
        bottomBar.child(doneBtn);

        return bottomBar;
    }
}
