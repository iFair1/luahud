package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.GuiTextures;
import me.ifair.luahud.gui.GuiUtils;
import me.ifair.luahud.gui.IconButtonWidget;
import me.ifair.luahud.gui.LuaHUDScreen;
import net.minecraft.client.gui.screen.Screen;

public class TopComponent {
    private static final String GITHUB_URL = "https://github.com/iFair1/luahud";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/luahud";
    // TODO: Discord

    private static final int MARGIN = 8;
    private static final int GAP = 4;
    private static final int BUTTON_SIZE = 20; // You should change the assets too. Else, bad things will happen

    private final LuaHUDScreen screen;
    private IconButtonWidget visibilityButton;
    private IconButtonWidget errorButton;

    public TopComponent(LuaHUDScreen screen) {
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
        screen.addWidget(
                new IconButtonWidget(
                        right1,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        GuiTextures.NO_ERRORS,
                        ignored -> {/* TODO: Open ErrorScreen */}
                )
        );
        this.visibilityButton = screen.addWidget(
                new IconButtonWidget(
                        right2,
                        y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        GuiTextures.SHOW_HIDDEN,
                        button -> {
                            boolean showHidden = !ConfigManager.isShowHiddenFilesEnabled();

                            ConfigManager.setShowHiddenFiles(showHidden);

                            this.visibilityButton.setIcon(
                                    showHidden ? GuiTextures.SHOW_HIDDEN : GuiTextures.HIDE_HIDDEN
                            );
                        }
                )
        );
    }
}
