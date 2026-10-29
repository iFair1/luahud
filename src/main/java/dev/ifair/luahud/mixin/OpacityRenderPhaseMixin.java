package dev.ifair.luahud.mixin;
//? if <1.21.5 {
import dev.ifair.luahud.render.Opacity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPhase.class)
public abstract class OpacityRenderPhaseMixin {
    @Inject(method = "startDrawing", at = @At("RETURN"))
    private void luahud$blend(CallbackInfo ci) {
        if (Opacity.current() < 1) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }
}
//?}
