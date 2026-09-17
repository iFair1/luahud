package me.ifair.luahud.gui;

import me.ifair.luahud.gui.components.BottomComponent;
import me.ifair.luahud.gui.components.TopComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class LuaHUDScreen extends Screen {
    private final Screen parent;

    public LuaHUDScreen(Screen screen) {
        super(Text.literal("LuaHUD"));
        this.parent = screen;
    }

    public <T extends Element & Drawable & Selectable> T addWidget(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    protected void init() {
        new TopComponent(this).build();
        new BottomComponent(this).build();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
