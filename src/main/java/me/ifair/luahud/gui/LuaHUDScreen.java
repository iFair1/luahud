package me.ifair.luahud.gui;

import me.ifair.luahud.gui.components.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class LuaHUDScreen extends Screen {
    private final Screen parent;
    private HeaderWidget header;
    private ProfileListWidget profileList;
    @Nullable
    private ProfileEntry keybindProfile;

    public LuaHUDScreen(Screen screen) {
        super(Text.literal("LuaHUD"));
        this.parent = screen;
    }

    public <T extends Element & Drawable & Selectable> T addWidget(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    protected void init() {
        header = new HeaderWidget(this);
        header.build();

        profileList = new ProfileListWidget(this);
        profileList.build();

        new FooterWidget(this).build();
    }

    @Override
    public void tick() {
        super.tick();
        if (header != null) header.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    public void reloadProfiles() {
        if (profileList != null) profileList.reloadProfiles();
    }

    public void openErrors() {
        MinecraftClient.getInstance().setScreen(new LuaHUDErrorsScreen(this, null));
    }

    public void selectKeybind(ProfileEntry profile) {
        if (keybindProfile != null) keybindProfile.setKeybindSelected(false);
        keybindProfile = profile;
        keybindProfile.setKeybindSelected(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (keybindProfile != null) {
            setSelectedKeybind(InputUtil.Type.MOUSE.createFromCode(button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keybindProfile != null) {
            setSelectedKeybind(keyCode == GLFW.GLFW_KEY_ESCAPE
                    ? InputUtil.UNKNOWN_KEY
                    : InputUtil.fromKeyCode(keyCode, scanCode));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && profileList != null
                && profileList.cancelNameEdit()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setSelectedKeybind(InputUtil.Key key) {
        ProfileEntry profile = keybindProfile;
        keybindProfile = null;
        if (profile != null) profile.setKeybind(key);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
