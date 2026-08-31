package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;

public class BottomBarComponent {
    // Move this to file handler or some similar class because I'll use this path in other places
    private static final File PROFILES_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("luahud/profiles/").toFile();

    public static FlowLayout create(Screen parentScreen) {
        FlowLayout bottomBar = Containers.horizontalFlow(Sizing.fill(85), Sizing.content());
        bottomBar.horizontalAlignment(HorizontalAlignment.CENTER);
        bottomBar.verticalAlignment(VerticalAlignment.CENTER);
        bottomBar.margins(Insets.top(6));
        bottomBar.gap(15);

        var openFolderBtn = Components.button(Text.literal("Open Folder"), button -> {
            if (!PROFILES_DIRECTORY.exists()) PROFILES_DIRECTORY.mkdirs();
            Util.getOperatingSystem().open(PROFILES_DIRECTORY);
        });
        openFolderBtn.sizing(Sizing.fixed(120), Sizing.fixed(20));

        var doneBtn = Components.button(Text.literal("Done"), button -> {
            parentScreen.close();
        });
        doneBtn.sizing(Sizing.fixed(100), Sizing.fixed(20));

        bottomBar.child(openFolderBtn);
        bottomBar.child(doneBtn);

        return bottomBar;
    }
}
