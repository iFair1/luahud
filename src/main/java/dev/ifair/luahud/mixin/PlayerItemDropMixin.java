package dev.ifair.luahud.mixin;

import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerItemDropMixin {
    // Inventory throws and cursor drops share this path, also used by creative screens.
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
            at = @At("RETURN"))
    private void luahud$notifyInventoryItemDrop(ItemStack stack, boolean retainOwnership,
                                               CallbackInfoReturnable<ItemEntity> cir) {
        // Modern vanilla returns null on the client even for a valid drop.
        if ((Object) this == MinecraftClient.getInstance().player && !stack.isEmpty()) {
            EventDispatcher.fireEvent("item-drop", stack.copy());
        }
    }
}
