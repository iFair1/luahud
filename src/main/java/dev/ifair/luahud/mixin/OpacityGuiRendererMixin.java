package dev.ifair.luahud.mixin;
//? if >=1.21.6 {
/*import dev.ifair.luahud.render.Opacity;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.*;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class OpacityGuiRendererMixin {
    @Inject(method = "prepareSimpleElement", at = @At("HEAD"))
    private void luahud$prepareSimpleElementHEAD(SimpleGuiElementRenderState state,
            //? if <1.21.9 {
            int depth,
            //?}
            CallbackInfo ci) { Opacity.prepare(state); Opacity.writingVertices(true); }
    @Inject(method = "prepareSimpleElement", at = @At("RETURN"))
    private void luahud$prepareSimpleElementRETURN(SimpleGuiElementRenderState state,
            //? if <1.21.9 {
            int depth,
            //?}
            CallbackInfo ci) { Opacity.writingVertices(false); Opacity.pop(); }
    @Inject(method = "prepareItem", at = @At("HEAD"))
    private void luahud$prepareItemHEAD(ItemGuiElementRenderState state, float u, float v, int x, int y, CallbackInfo ci) { Opacity.prepare(state); }
    @Inject(method = "prepareItem", at = @At("RETURN"))
    private void luahud$prepareItemRETURN(ItemGuiElementRenderState state, float u, float v, int x, int y, CallbackInfo ci) { Opacity.pop(); }
    @Inject(method = "prepareSpecialElement", at = @At("HEAD"))
    private void luahud$prepareSpecialElementHEAD(SpecialGuiElementRenderState state, int windowScale, CallbackInfo ci) { Opacity.prepare(state); }
    @Inject(method = "prepareSpecialElement", at = @At("RETURN"))
    private void luahud$prepareSpecialElementRETURN(SpecialGuiElementRenderState state, int windowScale, CallbackInfo ci) { Opacity.pop(); }
    @Inject(method = "method_71058", at = @At("HEAD"))
    private void luahud$method_71058HEAD(TextGuiElementRenderState state, CallbackInfo ci) { Opacity.prepare(state); }
    @Inject(method = "method_71058", at = @At("RETURN"))
    private void luahud$method_71058RETURN(TextGuiElementRenderState state, CallbackInfo ci) { Opacity.pop(); }
}
*///?}
