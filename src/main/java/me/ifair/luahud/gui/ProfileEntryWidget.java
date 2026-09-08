package me.ifair.luahud.gui;

import me.ifair.luahud.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;

final class ProfileEntryWidget extends ElementListWidget.Entry<ProfileEntryWidget> {
    private final ProfileEntry profile;
    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final LuaHUDScreen screen;
    private final ButtonWidget activeButton;
    private final ProfileNameField nameField;
    private final ButtonWidget hideButton;
    private final ButtonWidget keybindButton;
    private final ButtonWidget editButton;

    ProfileEntryWidget(ProfileEntry profile, MinecraftClient client, TextRenderer textRenderer, LuaHUDScreen screen) {
        this.profile = profile;
        this.client = client;
        this.textRenderer = textRenderer;
        this.screen = screen;
        this.activeButton = ButtonWidget.builder(
                Text.literal(profile.data().isActive() ? "On" : "Off"),
                button -> this.toggleActive()
        ).dimensions(0, 0, LuaHUDScreen.ACTIVE_BUTTON_WIDTH, LuaHUDScreen.ROW_HEIGHT - 4).build();

        this.nameField = new ProfileNameField(this, textRenderer, screen);
        this.nameField.setMaxLength(128);
        this.nameField.setEditableColor(0xFFFFFF);
        this.nameField.setText(profile.name());
        this.nameField.setCursorToStart(false);
        this.nameField.setSelectionEnd(0);
        this.nameField.setVisible(false);

        this.hideButton = ButtonWidget.builder(
                Text.literal(profile.data().isHidden() ? "Show" : "Hide"),
                button -> this.toggleHidden()
        ).dimensions(0, 0, LuaHUDScreen.HIDE_BUTTON_WIDTH, LuaHUDScreen.ROW_HEIGHT - 4).build();

        this.keybindButton = ButtonWidget.builder(Text.literal("Keybind"), button -> {
        }).dimensions(0, 0, LuaHUDScreen.KEYBIND_BUTTON_WIDTH, LuaHUDScreen.ROW_HEIGHT - 4).build();
        this.keybindButton.active = false;

        this.editButton = ButtonWidget.builder(
                Text.literal("Edit"),
                button -> LuaHUDScreen.editProfile(this.profile.name())
        ).dimensions(0, 0, LuaHUDScreen.EDIT_BUTTON_WIDTH, LuaHUDScreen.ROW_HEIGHT - 4).build();
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

        int activeButtonX = x;
        int editButtonX = x + entryWidth - LuaHUDScreen.EDIT_BUTTON_WIDTH;
        int keybindButtonX = editButtonX - LuaHUDScreen.ROW_CONTROL_GAP - LuaHUDScreen.KEYBIND_BUTTON_WIDTH;
        int hideButtonX = keybindButtonX - LuaHUDScreen.ROW_CONTROL_GAP - LuaHUDScreen.HIDE_BUTTON_WIDTH;
        int nameFieldX = activeButtonX + LuaHUDScreen.ACTIVE_BUTTON_WIDTH + LuaHUDScreen.ROW_CONTROL_GAP;
        int nameFieldWidth = Math.max(20, hideButtonX - LuaHUDScreen.ROW_CONTROL_GAP - nameFieldX);

        this.activeButton.setPosition(activeButtonX, y);
        this.nameField.setDimensionsAndPosition(nameFieldWidth, entryHeight, nameFieldX, y);
        this.hideButton.setPosition(hideButtonX, y);
        this.keybindButton.setPosition(keybindButtonX, y);
        this.editButton.setPosition(editButtonX, y);

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
        this.activeButton.render(context, mouseX, mouseY, tickDelta);

        if (this.nameField.isVisible()) {
            this.nameField.render(context, mouseX, mouseY, tickDelta);
        } else {
            String profileName = this.textRenderer.trimToWidth(this.nameField.getText(), this.nameField.getInnerWidth());
            context.drawTextWithShadow(this.textRenderer, profileName, this.nameField.getX() + 4, textY, 0xFFFFFF);
        }

        this.hideButton.render(context, mouseX, mouseY, tickDelta);
        this.keybindButton.render(context, mouseX, mouseY, tickDelta);
        this.editButton.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public List<? extends Element> children() {
        return List.of(this.activeButton, this.nameField, this.hideButton, this.keybindButton, this.editButton);
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of(this.activeButton, this.nameField, this.hideButton, this.keybindButton, this.editButton);
    }

    void cancelEditing() {
        this.nameField.setText(this.profile.name());
        this.nameField.setFocused(false);
    }

    String profileName() {
        return this.profile.name();
    }

    boolean isNameFieldMouseOver(double mouseX, double mouseY) {
        return this.nameField.isMouseOver(mouseX, mouseY);
    }

    private void toggleActive() {
        if (ConfigManager.setProfileActive(this.profile.name(), !this.profile.data().isActive())) {
            this.client.execute(this.screen::refreshProfiles);
        }
    }

    private void toggleHidden() {
        if (ConfigManager.setProfileHidden(this.profile.name(), !this.profile.data().isHidden())) {
            this.client.execute(this.screen::refreshProfiles);
        }
    }

    void commitRename() {
        String newName = this.nameField.getText().trim();
        boolean changed = !this.profile.name().equals(newName);
        boolean renamed = ConfigManager.renameProfile(this.profile.name(), newName);

        this.nameField.setFocused(false);
        if (renamed && changed) {
            this.screen.refreshProfiles();
        } else if (!renamed) {
            this.nameField.setText(this.profile.name());
        }
    }
}
