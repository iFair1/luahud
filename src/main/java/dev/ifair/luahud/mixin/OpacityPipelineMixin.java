package dev.ifair.luahud.mixin;
//? if =1.21.5 {
/*import dev.ifair.luahud.render.Opacity;
import dev.ifair.luahud.compat.OpacityPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.render.RenderLayer$MultiPhase")
public abstract class OpacityPipelineMixin {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private RenderPipeline luahud$blend(RenderPipeline pipeline) {
        return Opacity.current() < 1 ? OpacityPipeline.blended(pipeline) : pipeline;
    }
}
*///?}
