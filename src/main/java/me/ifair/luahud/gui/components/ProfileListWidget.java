package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.LuaHUDScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;

public class ProfileListWidget extends ElementListWidget<ProfileEntry> {
    private static final int TOP_MARGIN = HeaderWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;
    private static final int BOTTOM_MARGIN = FooterWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;

    private static final int ROW_HEIGHT = 16;

    private final LuaHUDScreen screen;

    public ProfileListWidget(LuaHUDScreen screen) {
        super(
                MinecraftClient.getInstance(),
                screen.width,
                screen.height - TOP_MARGIN - BOTTOM_MARGIN,
                TOP_MARGIN,
                ROW_HEIGHT
        );
        this.screen = screen;
    }

    public void build() {
        int y1 = TOP_MARGIN;
        int y2 = screen.height - BOTTOM_MARGIN;
        // it takes full screen width so no x calculation
    }

    public void reloadProfiles() {
        clearEntries();

        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();

        ConfigManager.getProfiles().stream()
                .filter(profile -> showHidden || !profile.isHidden())
                .forEach(profile -> {
                    addEntry(new ProfileEntry(profile.getName(), profile));
                });
    }
}
