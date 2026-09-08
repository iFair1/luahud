package me.ifair.luahud.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class ProfileNameField extends TextFieldWidget {
    private final ProfileEntryWidget owner;
    private final LuaHUDScreen screen;

    ProfileNameField(ProfileEntryWidget owner, TextRenderer textRenderer, LuaHUDScreen screen) {
        super(textRenderer, 0, 0, Text.literal("Profile name"));
        this.owner = owner;
        this.screen = screen;
    }

    @Override
    public void setFocused(boolean focused) {
        ProfileEntryWidget focusedProfile = this.screen.getFocusedProfile();
        if (focused && focusedProfile != null && focusedProfile != this.owner) {
            focusedProfile.cancelEditing();
        }

        boolean wasFocused = this.isFocused();
        super.setFocused(focused);

        if (this.isFocused()) {
            this.screen.setFocusedProfile(this.owner);
        } else if (wasFocused) {
            this.setText(this.owner.profileName());
            if (this.screen.getFocusedProfile() == this.owner) {
                this.screen.setFocusedProfile(null);
            }
        }
    }
}
