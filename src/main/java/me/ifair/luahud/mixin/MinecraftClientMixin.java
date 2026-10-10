package me.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    // minecraft only updates the gpu usage field when f3 is opened.
    @ModifyExpressionValue(
            method = "render(Z)V",
            //? if <1.21.9 {
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/DebugHud;shouldShowDebugHud()Z"),
            //?} else {
            /*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/debug/DebugHudProfile;isEntryVisible(Lnet/minecraft/util/Identifier;)Z"),
            *///?}
            require = 1,
            allow = 1
    )
    private boolean luahud$keepGpuMeasurementEnabled(boolean original) {
        return true;
    }
}
