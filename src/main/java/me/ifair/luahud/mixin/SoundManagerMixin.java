package me.ifair.luahud.mixin;

import me.ifair.luahud.sound.SoundState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    //? if <1.21.6 {
    @Inject(method = "pauseAll", at = @At("HEAD"))
    private void luahud$pauseRecords(CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "pauseAllExcept", at = @At("HEAD"))
    private void luahud$pauseRecords(net.minecraft.sound.SoundCategory[] exclusions, CallbackInfo ci) {
        for (var category : exclusions) {
            if (category == net.minecraft.sound.SoundCategory.RECORDS) return;
        }
    *///?}
        var world = MinecraftClient.getInstance().world;
        if (world != null) ((SoundState) world).luahud$setSoundPaused(true);
    }

    @Inject(method = "resumeAll", at = @At("HEAD"))
    private void luahud$resumeRecords(CallbackInfo ci) {
        var world = MinecraftClient.getInstance().world;
        if (world != null) ((SoundState) world).luahud$setSoundPaused(false);
    }
}
