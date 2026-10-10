//? if >=1.21.6 && <1.21.9 {
/*package me.ifair.luahud.mixin;

import me.ifair.luahud.render.GuiEntityLight;
import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EntityGuiElementRenderer.class)
public abstract class EntityGuiLightingMixin {
    @ModifyConstant(
            method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            constant = @Constant(intValue = 0xF000F0), require = 1, allow = 1)
    private int luahud$useIconLighting(int original, EntityGuiElementRenderState element, MatrixStack matrices) {
        int light = ((GuiEntityLight) element.renderState()).luahud$getGuiLight();
        return light < 0 ? original : light;
    }
}
*///?}
