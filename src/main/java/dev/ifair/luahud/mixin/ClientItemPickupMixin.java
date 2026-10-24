package dev.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientItemPickupMixin {
    // Vanilla reads the stack on the client thread, before decrementing or removing it.
    @WrapOperation(method = "onItemPickupAnimation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ItemEntity;getStack()Lnet/minecraft/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack luahud$notifyLocalPlayerPickup(ItemEntity entity, Operation<ItemStack> original,
                                                   ItemPickupAnimationS2CPacket packet) {
        ItemStack stack = original.call(entity);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && packet.getCollectorEntityId() == player.getId()
                && packet.getStackAmount() > 0 && !stack.isEmpty()) {
            EventDispatcher.fireEvent("item-pickup", stack.copyWithCount(packet.getStackAmount()));
        }
        return stack;
    }
}
