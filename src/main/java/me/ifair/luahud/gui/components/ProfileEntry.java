package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.GuiTextures;
import me.ifair.luahud.gui.IconButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ProfileEntry extends ElementListWidget.Entry<ProfileEntry> {
    public static final int ROW_HEIGHT = 20;
    private static final int BUTTON_SIZE = 16;
    private static final int GAP = ROW_HEIGHT - BUTTON_SIZE;
    private static final int LABEL_Y_OFFSET = 1;

    private static final int KEYBIND_WIDTH = 72;

    private final ProfileListWidget list;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final IconButtonWidget activeButton;
    private final TextFieldWidget nameField;
    private final ButtonWidget keybindButton;
    private final IconButtonWidget visibilityButton;
    private final IconButtonWidget moveUpButton;
    private final IconButtonWidget moveDownButton;
    private final IconButtonWidget deleteButton;

    private boolean deleteConfirmation;
    private boolean finishingNameEdit;

    // Separate values so I can do an O(1) access
    private String profileName;
    private String keybind;
    private boolean active;
    private boolean hidden;

    public ProfileEntry(ProfileListWidget list, ConfigData.ProfileData profile) {
        this.list = list;
        this.profileName = profile.getName();
        this.keybind = profile.getKeybind();
        this.active = profile.isActive();
        this.hidden = profile.isHidden();

        this.activeButton = iconButton(
                profile.isActive() ? GuiTextures.ENABLED : GuiTextures.DISABLED,
                ignored -> toggleActive()
        );

        this.nameField = new TextFieldWidget(client.textRenderer, 0, 0, Text.literal("Profile name")) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    cancelNameEdit();
                    return true;
                }

                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    applyNameEdit();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            @Override
            public void setFocused(boolean focused) {
                boolean wasFocused = isFocused();
                super.setFocused(focused);
                if (wasFocused && !isFocused() && !finishingNameEdit) {
                    setText(profileName);
                }
            }
        };

        this.nameField.setText(profileName);
        this.nameField.setVisible(false);

        this.visibilityButton = iconButton(
                hidden ? GuiTextures.HIDDEN : GuiTextures.SHOWN,
                ignored -> toggleHidden()
        );

        this.keybindButton = ButtonWidget.builder(getKeybindText(), ignored -> list.selectKeybind(this))
                .dimensions(0, 0, KEYBIND_WIDTH, BUTTON_SIZE)
                .build();

        this.moveUpButton = iconButton(
                GuiTextures.MOVE_UP,
                ignored -> list.moveProfile(profileName, -1)
        );

        this.moveDownButton = iconButton(
                GuiTextures.MOVE_DOWN,
                ignored -> list.moveProfile(profileName, 1)
        );

        this.deleteButton = iconButton(
                GuiTextures.DELETE, ignored -> deleteProfile()
        );
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
        int deleteX = x + entryWidth - BUTTON_SIZE;
        int downX = deleteX - GAP - BUTTON_SIZE;
        int upX = downX - GAP - BUTTON_SIZE;
        int keybindX = upX - GAP - KEYBIND_WIDTH;
        int visibilityX = keybindX - GAP - BUTTON_SIZE;
        int nameX = x + BUTTON_SIZE + GAP;
        int nameWidth = visibilityX - GAP - nameX;

        activeButton.setPosition(x, y);
        nameField.setDimensionsAndPosition(nameWidth, BUTTON_SIZE, nameX, y);
        visibilityButton.setPosition(visibilityX, y);
        keybindButton.setPosition(keybindX, y);
        moveUpButton.setPosition(upX, y);
        moveDownButton.setPosition(downX, y);
        deleteButton.setPosition(deleteX, y);

        if (!nameField.isFocused()) {
            nameField.setCursorToStart(false);
            nameField.setSelectionEnd(0);
        }

        boolean hoveringName = mouseX >= nameField.getX()
                && mouseX < nameField.getRight()
                && mouseY >= nameField.getY()
                && mouseY < nameField.getBottom();
        nameField.setVisible(hoveringName || nameField.isFocused());

        activeButton.render(context, mouseX, mouseY, tickDelta);
        if (nameField.isVisible()) {
            nameField.render(context, mouseX, mouseY, tickDelta);
        } else {
            String name = client.textRenderer.trimToWidth(profileName, nameField.getInnerWidth());
            int textY = y + (BUTTON_SIZE - client.textRenderer.fontHeight) / 2 + LABEL_Y_OFFSET;
            context.drawTextWithShadow(client.textRenderer, name, nameX + 4, textY, 0xFFFFFF);
        }
        visibilityButton.render(context, mouseX, mouseY, tickDelta);
        keybindButton.render(context, mouseX, mouseY, tickDelta);
        moveUpButton.render(context, mouseX, mouseY, tickDelta);
        moveDownButton.render(context, mouseX, mouseY, tickDelta);
        deleteButton.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public List<? extends Element> children() {
        return List.of(activeButton, nameField, visibilityButton, keybindButton, moveUpButton,moveDownButton, deleteButton);
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of(activeButton, nameField, visibilityButton, keybindButton, moveUpButton,moveDownButton, deleteButton);
    }

    public void setKeybind(InputUtil.Key key) {
        ConfigManager.setProfileKeybind(profileName, key.getTranslationKey());
        this.keybind = key.getTranslationKey();
        keybindButton.setMessage(getKeybindText());
    }

    public void setKeybindSelected(boolean selected) {
        Text text = getKeybindText();
        keybindButton.setMessage(selected
                ? Text.literal("> ")
                .append(text.copy().formatted(Formatting.WHITE, Formatting.UNDERLINE))
                .append(" <")
                .formatted(Formatting.YELLOW)
                : text);
    }

    private void toggleActive() {
        boolean newValue = !active;
        active = newValue;
        activeButton.setIcon(newValue ? GuiTextures.ENABLED : GuiTextures.DISABLED);
        ConfigManager.setProfileActive(profileName, newValue);
        list.reloadProfiles();
    }

    private void toggleHidden() {
        boolean newValue = !hidden;
        hidden = newValue;
        visibilityButton.setIcon(newValue ? GuiTextures.HIDDEN : GuiTextures.SHOWN);
        ConfigManager.setProfileHidden(profileName, newValue);
        list.reloadProfiles();
    }

    private void deleteProfile() {
        if (!deleteConfirmation) {
            deleteConfirmation = true;
            deleteButton.setIcon(GuiTextures.DELETE_CONFIRM);
            return;
        }

        ConfigManager.deleteProfile(profileName);
        list.reloadProfiles();
    }

    private void cancelNameEdit() {
        finishingNameEdit = true;
        nameField.setText(profileName);
        nameField.setFocused(false);
        finishingNameEdit = false;
    }

    public boolean cancelNameEditIfFocused() {
        if (!nameField.isFocused()) return false;
        cancelNameEdit();
        return true;
    }

    private void applyNameEdit() {
        String newName = nameField.getText();
        ConfigManager.renameProfile(profileName, newName);
        profileName = newName;
        finishingNameEdit = true;
        nameField.setText(profileName);
        nameField.setFocused(false);
        finishingNameEdit = false;
        list.reloadProfiles();
    }

    private Text getKeybindText() {
        return InputUtil.fromTranslationKey(this.keybind).getLocalizedText();
    }

    private static IconButtonWidget iconButton(Identifier icon, ButtonWidget.PressAction onPress) {
        return new IconButtonWidget(
                0,
                0,
                BUTTON_SIZE,
                BUTTON_SIZE,
                icon,
                onPress
        );
    }
}
