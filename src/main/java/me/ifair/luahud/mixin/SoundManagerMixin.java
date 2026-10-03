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
    @Inject(method = "pauseAll", at = @At("HEAD"))
    private void luahud$pauseRecords(CallbackInfo ci) {
        ((SoundState) MinecraftClient.getInstance().worldRenderer).luahud$setSoundPaused(true);
    }

    @Inject(method = "resumeAll", at = @At("HEAD"))
    private void luahud$resumeRecords(CallbackInfo ci) {
        ((SoundState) MinecraftClient.getInstance().worldRenderer).luahud$setSoundPaused(false);
    }
}
