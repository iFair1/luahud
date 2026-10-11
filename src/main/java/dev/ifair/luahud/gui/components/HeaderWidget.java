package dev.ifair.luahud.gui.components;

import dev.ifair.luahud.config.ConfigManager;
import dev.ifair.luahud.error.ErrorManager;
import dev.ifair.luahud.gui.GuiTextures;
import dev.ifair.luahud.gui.GuiUtils;
import dev.ifair.luahud.gui.IconButtonWidget;
import dev.ifair.luahud.gui.LuaHUDScreen;
import net.minecraft.util.Identifier;

public class HeaderWidget {
    private static final String GITHUB_URL = "https://github.com/iFair1/luahud";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/luahud";
    // TODO: Discord
    // TODO: Documentation

    public static final int MARGIN = 8;
    private static final int GAP = 4;
    public static final int BUTTON_SIZE = 20; // You should change the assets too. Else, bad things will happen

    private final LuaHUDScreen screen;
    private IconButtonWidget visibilityButton;
    private IconButtonWidget errorButton;

    public HeaderWidget(LuaHUDScreen screen) {
        this.screen = screen;
    }

    public void build() {
        int y = MARGIN;

        int left1 = MARGIN;
        int left2 = left1 + BUTTON_SIZE + GAP;
        int left3 = left2 + BUTTON_SIZE + GAP;

        int right1 = screen.width - MARGIN - BUTTON_SIZE;
        int right2 = right1 - GAP - BUTTON_SIZE;

        // Left
        screen.addWidget(
                new IconButtonWidget(
                        left1,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        GuiTextures.GITHUB,
                        ignored -> GuiUtils.openInBrowser(GITHUB_URL)
                )
        );
        screen.addWidget(
                new IconButtonWidget(
                        left2,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        GuiTextures.MODRINTH,
                        ignored -> GuiUtils.openInBrowser(MODRINTH_URL)
                )
        );
        screen.addWidget(
                new IconButtonWidget(
                left3,
                y,
                BUTTON_SIZE,
                BUTTON_SIZE,
                GuiTextures.DISCORD,
                ignored -> {}
            )
        );


        // Right
        this.errorButton = screen.addWidget(
                new IconButtonWidget(
                        right1,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        errorIcon(),
                        ignored -> screen.openErrors()
                )
        );
        this.visibilityButton = screen.addWidget(
                new IconButtonWidget(
                        right2,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        ConfigManager.isShowHiddenFilesEnabled()
                                ? GuiTextures.HIDE_HIDDEN
                                : GuiTextures.SHOW_HIDDEN,
                        button -> {
                            boolean showHidden = !ConfigManager.isShowHiddenFilesEnabled();

                            ConfigManager.setShowHiddenFiles(showHidden);
                            this.visibilityButton.setIcon(
                                    showHidden ? GuiTextures.HIDE_HIDDEN : GuiTextures.SHOW_HIDDEN
                            );
                            screen.reloadProfiles();
                        }
                )
        );
    }

    public void tick() {
        if (this.errorButton != null) this.errorButton.setIcon(errorIcon());
    }

    private static Identifier errorIcon() {
        return ErrorManager.distinctCount() == 0 ? GuiTextures.NO_ERRORS : GuiTextures.ERRORS;
    }
}
