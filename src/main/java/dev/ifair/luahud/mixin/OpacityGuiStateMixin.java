package dev.ifair.luahud.mixin;
//? if >=1.21.6 {
/*import dev.ifair.luahud.render.Opacity;
import net.minecraft.client.gui.render.state.*;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class OpacityGuiStateMixin {
    @Inject(method = "addSimpleElement", at = @At("HEAD"))
    private void luahud$addSimpleElement(SimpleGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "addPreparedTextElement", at = @At("HEAD"))
    private void luahud$addPreparedTextElement(SimpleGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "addSimpleElementToCurrentLayer", at = @At("HEAD"))
    private void luahud$addSimpleElementToCurrentLayer(TexturedQuadGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "addItem", at = @At("HEAD"))
    private void luahud$addItem(ItemGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "addText", at = @At("HEAD"))
    private void luahud$addText(TextGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "addSpecialElement", at = @At("HEAD"))
    private void luahud$addSpecialElement(SpecialGuiElementRenderState state, CallbackInfo ci) { Opacity.capture(state); }
    @Inject(method = "clear", at = @At("RETURN"))
    private void luahud$clear(CallbackInfo ci) { Opacity.clear(); }
}
*///?}
