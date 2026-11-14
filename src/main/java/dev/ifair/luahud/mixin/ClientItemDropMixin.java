package dev.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientItemDropMixin {
    // Q/Ctrl+Q removes the stack locally without calling PlayerEntity.dropItem.
    @WrapOperation(method = "dropSelectedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;dropSelectedItem(Z)Lnet/minecraft/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack luahud$notifySelectedItemDrop(PlayerInventory inventory, boolean entireStack,
                                                  Operation<ItemStack> original) {
        ItemStack stack = original.call(inventory, entireStack);
        if (!stack.isEmpty()) {
            EventDispatcher.fireEvent("item-drop", stack.copy());
        }
        return stack;
    }
}
