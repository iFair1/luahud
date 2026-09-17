package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.LuaHUDScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class FooterWidget {
    private static final int GAP = 8;
    public static final int MARGIN = 8;
    private static final int BUTTON_WIDTH = 150;
    public static final int BUTTON_HEIGHT = 20;

    private final LuaHUDScreen screen;

    public FooterWidget(LuaHUDScreen screen) { this.screen = screen; }

    public void build() {

        int y = screen.height - MARGIN - BUTTON_HEIGHT;

        int x1 = (screen.width - (BUTTON_WIDTH * 2 + GAP)) / 2;
        int x2 = x1 + BUTTON_WIDTH + GAP;

        screen.addWidget(
                ButtonWidget.builder(Text.literal("Open Folder"), ignored -> {
                            Util.getOperatingSystem().open(ConfigManager.getScriptsDir());
                        })
                        .dimensions(x1, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );

        screen.addWidget(
                ButtonWidget.builder(Text.literal("Done"), ignored -> { screen.close(); })
                        .dimensions(x2,y,BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
    }
}
