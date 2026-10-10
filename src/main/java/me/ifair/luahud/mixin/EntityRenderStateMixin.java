//? if >=1.21.6 && <1.21.9 {
/*package me.ifair.luahud.mixin;

import me.ifair.luahud.render.GuiEntityLight;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements GuiEntityLight {
    @Unique private int luahud$guiLight = -1;

    @Override
    public int luahud$getGuiLight() {
        return luahud$guiLight;
    }

    @Override
    public void luahud$setGuiLight(int light) {
        luahud$guiLight = light;
    }
}
*///?}
