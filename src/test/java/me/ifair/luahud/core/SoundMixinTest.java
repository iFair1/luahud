package me.ifair.luahud.core;

import me.ifair.luahud.sound.SoundState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundMixinTest {
    @Test void worldAndPlaybackOwnerReceiveSoundTrackingMixins() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        // Loading without initialization applies mixins without creating a window.
        Class<?> world = Class.forName("net.minecraft.client.world.ClientWorld", false, loader);
        //? if <1.21.2 {
        Class<?> owner = Class.forName("net.minecraft.client.render.WorldRenderer", false, loader);
        //?} else {
        /*Class<?> owner = Class.forName("net.minecraft.client.world.WorldEventHandler", false, loader);
        *///?}
        Class.forName("net.minecraft.client.sound.SoundManager", false, loader);
        Class.forName("net.minecraft.client.MinecraftClient", false, loader);
        //? if >=1.21.6 && <1.21.9 {
        /*Class<?> renderState = Class.forName("net.minecraft.client.render.entity.state.EntityRenderState", false, loader);
        Class.forName("net.minecraft.client.gui.render.EntityGuiElementRenderer", false, loader);
        assertTrue(me.ifair.luahud.render.GuiEntityLight.class.isAssignableFrom(renderState));
        *///?}
        assertTrue(SoundState.class.isAssignableFrom(world));
        assertTrue(SoundState.class.isAssignableFrom(owner));
    }
}
