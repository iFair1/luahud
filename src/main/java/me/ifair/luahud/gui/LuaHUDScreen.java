package me.ifair.luahud.gui;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LuaHUDScreen extends Screen {
    private static final String GITHUB_URL = "https://github.com/iFair1/luahud";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/luahud";
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    private static final int CONTENT_MAX_WIDTH = 540;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private List<ProfileEntry> profiles = List.of();
    private ProfileEntryWidget focusedProfile;
    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listBottom;

    public LuaHUDScreen(@Nullable Screen parent) {
        super(Text.literal("LuaHUD"));
        this.parent = parent;
    }

    public LuaHUDScreen() {
        this(null);
    }

    @Override
    protected void init() {
        this.contentWidth = Math.min(CONTENT_MAX_WIDTH, this.width - 24);
        this.contentLeft = (this.width - this.contentWidth) / 2;
        this.listTop = 52;
        this.listBottom = Math.max(this.listTop + ROW_HEIGHT, this.height - 40);

        reloadProfiles();
        this.addDrawableChild(new ProfileListWidget(
                this.client,
                this.width,
                this.listBottom - this.listTop,
                this.listTop,
                ROW_HEIGHT
        ));

        int topY = 20;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("GitHub"), button -> openInBrowser(GITHUB_URL))
                .dimensions(this.contentLeft, topY, 60, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Modrinth"), button -> openInBrowser(MODRINTH_URL))
                .dimensions(this.contentLeft + 64, topY, 70, BUTTON_HEIGHT)
                .build());

        String hiddenLabel = ConfigManager.isShowHiddenFilesEnabled() ? "Hidden: On" : "Hidden: Off";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(hiddenLabel), button -> {
                    ConfigManager.setShowHiddenFiles(!ConfigManager.isShowHiddenFilesEnabled());
                    this.clearAndInit();
                })
                .dimensions(this.contentLeft + this.contentWidth - 100, topY, 100, BUTTON_HEIGHT)
                .build());

        int footerY = this.height - 28;
        int footerButtonWidth = Math.min(150, (this.contentWidth - 10) / 2);
        int footerWidth = footerButtonWidth * 2 + 10;
        int footerX = (this.width - footerWidth) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Folder"), button -> openProfilesFolder())
                .dimensions(footerX, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(footerX + footerButtonWidth + 10, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 26, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (this.focusedProfile != null && !this.focusedProfile.nameField.isMouseOver(mouseX, mouseY)) {
            this.focusedProfile.cancelEditing();
            return true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.focusedProfile != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.focusedProfile.cancelEditing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.focusedProfile.commitRename();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void reloadProfiles() {
        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();
        this.profiles = ConfigManager.getProfiles().entrySet().stream()
                .filter(entry -> showHidden || !entry.getValue().hidden)
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ProfileEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void openProfilesFolder() {
        try {
            Files.createDirectories(PROFILES_DIR);
            Util.getOperatingSystem().open(PROFILES_DIR);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("Error while opening profiles directory at {}: {}", PROFILES_DIR, e.getMessage());
        }
    }

    private static void openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while directing to url \"{}\": {}", url, e.getMessage());
        }
    }

    private record ProfileEntry(String name, ConfigData.ProfileData data) {
    }

    private class ProfileListWidget extends ElementListWidget<ProfileEntryWidget> {
        private ProfileListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
            profiles.forEach(profile -> this.addEntry(new ProfileEntryWidget(profile)));
        }

        @Override
        public int getRowWidth() {
            return Math.min(CONTENT_MAX_WIDTH, this.getWidth() - 32);
        }

        @Override
        protected int getScrollbarX() {
            return this.getRight() - 8;
        }

        @Override
        protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
            if (this.getEntryCount() == 0) {
                context.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.literal("No Lua profiles found"),
                        width / 2,
                        this.getY() + (this.getHeight() - textRenderer.fontHeight) / 2,
                        0xAAAAAA
                );
            }
        }
    }

    private class ProfileEntryWidget extends ElementListWidget.Entry<ProfileEntryWidget> {
        private final ProfileEntry profile;
        private final ProfileNameField nameField;

        private ProfileEntryWidget(ProfileEntry profile) {
            this.profile = profile;
            this.nameField = new ProfileNameField(this);
            this.nameField.setMaxLength(128);
            this.nameField.setEditableColor(0xFFFFFF);
            this.nameField.setText(profile.name());
            this.nameField.setCursorToStart(false);
            this.nameField.setSelectionEnd(0);
            this.nameField.setVisible(false);
        }

        @Override
        public void render(
                DrawContext context,
                int index,
                int y,
                int x,
                int entryWidth,
                int entryHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float tickDelta
        ) {
            int background = index % 2 == 0 ? 0x44000000 : 0x22000000;
            context.fill(x, y, x + entryWidth, y + entryHeight, background);

            int nameFieldWidth = Math.max(20, entryWidth - 92);
            this.nameField.setDimensionsAndPosition(nameFieldWidth, entryHeight, x + 31, y);
            if (!this.nameField.isFocused()) {
                this.nameField.setCursorToStart(false);
                this.nameField.setSelectionEnd(0);
            }

            boolean hoveringName = mouseX >= this.nameField.getX()
                    && mouseX < this.nameField.getRight()
                    && mouseY >= this.nameField.getY()
                    && mouseY < this.nameField.getBottom();
            this.nameField.setVisible(hoveringName || this.nameField.isFocused());

            int textY = this.nameField.getY() + (this.nameField.getHeight() - 8) / 2;
            int statusColor = this.profile.data().active ? 0x55FF55 : 0xFF5555;
            context.drawTextWithShadow(textRenderer, this.profile.data().active ? "On" : "Off", x + 5, textY, statusColor);

            if (this.nameField.isVisible()) {
                this.nameField.render(context, mouseX, mouseY, tickDelta);
            } else {
                String profileName = textRenderer.trimToWidth(this.nameField.getText(), this.nameField.getInnerWidth());
                context.drawTextWithShadow(textRenderer, profileName, this.nameField.getX() + 4, textY, 0xFFFFFF);
            }

            if (this.profile.data().hidden) {
                context.drawTextWithShadow(textRenderer, "Hidden", x + entryWidth - 45, textY, 0xAAAAAA);
            }
        }

        @Override
        public List<? extends Element> children() {
            return List.of(this.nameField);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(this.nameField);
        }

        private void cancelEditing() {
            this.nameField.setText(this.profile.name());
            this.nameField.setFocused(false);
        }

        private void commitRename() {
            String newName = this.nameField.getText().trim();
            boolean changed = !this.profile.name().equals(newName);
            boolean renamed = ConfigManager.renameProfile(this.profile.name(), newName);

            this.nameField.setFocused(false);
            if (renamed && changed) {
                LuaHUDScreen.this.clearAndInit();
            } else if (!renamed) {
                this.nameField.setText(this.profile.name());
            }
        }
    }

    private class ProfileNameField extends TextFieldWidget {
        private final ProfileEntryWidget owner;

        private ProfileNameField(ProfileEntryWidget owner) {
            super(textRenderer, 0, 0, Text.literal("Profile name"));
            this.owner = owner;
        }

        @Override
        public void setFocused(boolean focused) {
            if (focused && focusedProfile != null && focusedProfile != this.owner) {
                focusedProfile.cancelEditing();
            }

            boolean wasFocused = this.isFocused();
            super.setFocused(focused);

            if (this.isFocused()) {
                focusedProfile = this.owner;
            } else if (wasFocused) {
                this.setText(this.owner.profile.name());
                if (focusedProfile == this.owner) {
                    focusedProfile = null;
                }
            }
        }
    }
}
