package dev.ifair.luahud.mixin;

import dev.ifair.luahud.sound.RecordTracker;
import dev.ifair.luahud.sound.SoundState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Exposes the world's sound tracker independently of its vanilla owner. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldSoundMixin implements SoundState {
    //? if >=1.21.2 {
    /*@Shadow @Final private net.minecraft.client.world.WorldEventHandler worldEventHandler;
    *///?}

    @Unique
    private SoundState luahud$soundState() {
        //? if <1.21.2 {
        return (SoundState) MinecraftClient.getInstance().worldRenderer;
        //?} else {
        /*return (SoundState) worldEventHandler;
        *///?}
    }

    @Override
    public RecordTracker.PlayingRecord luahud$getRecord() {
        return luahud$soundState().luahud$getRecord();
    }

    @Override
    public void luahud$setSoundPaused(boolean paused) {
        luahud$soundState().luahud$setSoundPaused(paused);
    }
}
