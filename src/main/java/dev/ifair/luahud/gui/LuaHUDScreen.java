package dev.ifair.luahud.gui;

import dev.ifair.luahud.gui.components.FooterWidget;
import dev.ifair.luahud.gui.components.HeaderWidget;
import dev.ifair.luahud.gui.components.ProfileEntry;
import dev.ifair.luahud.gui.components.ProfileListWidget;
import dev.ifair.luahud.gui.components.*;
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
        if (profileList != null) profileList.refreshIfChanged();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
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
    //? if <1.21.9 {
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //?} else {
    /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int button = click.button();
    *///?}
        if (keybindProfile != null) {
            setSelectedKeybind(InputUtil.Type.MOUSE.createFromCode(button));
            return true;
        }
        //? if <1.21.9 {
        return super.mouseClicked(mouseX, mouseY, button);
        //?} else {
        /*return super.mouseClicked(click, doubled);
        *///?}
    }

    @Override
    //? if <1.21.9 {
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //?} else {
    /*public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
    *///?}
        if (keybindProfile != null) {
            setSelectedKeybind(keyCode == GLFW.GLFW_KEY_ESCAPE
                    ? InputUtil.UNKNOWN_KEY
                    //? if <1.21.9 {
                    : InputUtil.fromKeyCode(keyCode, scanCode));
                    //?} else {
                    /*: InputUtil.fromKeyCode(input));
                    *///?}
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && profileList != null
                && profileList.cancelNameEdit()) {
            return true;
        }
        //? if <1.21.9 {
        return super.keyPressed(keyCode, scanCode, modifiers);
        //?} else {
        /*return super.keyPressed(input);
        *///?}
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
