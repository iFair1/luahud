package dev.ifair.luahud.mixin;

//? if >=1.21.6 {
/*import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawContext.class)
public interface CanvasDrawContextAccessor {
    @Accessor("state") GuiRenderState luahud$getState();
    @Accessor("scissorStack") DrawContext.ScissorStack luahud$getScissorStack();
}
*///?} else {
/** The deferred GUI state accessor is only required on Minecraft 1.21.6 and later. */
public interface CanvasDrawContextAccessor {}
//?}
