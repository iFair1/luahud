package dev.ifair.luahud.mixin;

import dev.ifair.luahud.render.Opacity;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if >=1.21.6 {
/*@Mixin(BufferBuilder.class)
public abstract class OpacityVertexMixin {
    @ModifyVariable(method = "color(IIII)Lnet/minecraft/client/render/VertexConsumer;", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int luahud$alpha(int alpha) { return Opacity.vertexAlpha(alpha); }

    @ModifyVariable(method = "putColor", at = @At("HEAD"), argsOnly = true)
    private static int luahud$color(int color) { return Opacity.vertexColor(color); }
}

*///?}
