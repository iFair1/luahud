package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.LuaHUDScreen;
import me.ifair.luahud.gui.ProfileEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ElementListWidget;

public class ProfileListWidget extends ElementListWidget<ProfileEntry> {
    private static final int MAX_ROW_WIDTH = 540;
    private static final int TOP_MARGIN = HeaderWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;
    private static final int BOTTOM_MARGIN = FooterWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;

    private final LuaHUDScreen screen;

    public ProfileListWidget(LuaHUDScreen screen) {
        super(
                MinecraftClient.getInstance(),
                screen.width,
                screen.height - TOP_MARGIN - BOTTOM_MARGIN,
                TOP_MARGIN,
                ProfileEntry.ROW_HEIGHT
        );
        this.screen = screen;
    }

    public void build() {
        reloadProfiles();
        screen.addWidget(this);
    }

    public void reloadProfiles() {
        ProfileEntry focused = getFocused();
        if (focused != null) focused.setFocused(null);
        setFocused((Element) null);
        clearEntries();

        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();

        ConfigManager.getProfiles().stream()
                .filter(profile -> showHidden || !profile.isHidden())
                .forEach(profile -> {
                    addEntry(new ProfileEntry(this, profile));
                });
    }

    public void selectKeybind(ProfileEntry entry) {
        screen.selectKeybind(entry);
    }

    public void moveProfile(String profileName, int offset) {
        if (ConfigManager.moveProfile(profileName, offset)) requestReload();
    }

    public void requestReload() {
        client.send(this::reloadProfiles);
    }

    public boolean cancelNameEdit() {
        ProfileEntry focused = getFocused();
        return focused != null && focused.cancelNameEditIfFocused();
    }

    @Override
    public int getRowWidth() {
        return Math.min(MAX_ROW_WIDTH, getWidth() - 32);
    }

    @Override
    protected int getScrollbarX() {
        return getRight() - 8;
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
        if (children().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    net.minecraft.text.Text.literal("No Lua profiles found"),
                    getWidth() / 2,
                    getY() + (getHeight() - client.textRenderer.fontHeight) / 2,
                    0xAAAAAA
            );
        }
    }
}
