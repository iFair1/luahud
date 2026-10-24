package dev.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DamageTracker;
import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientDamageMixin {
    @Unique private final DamageTracker luahud$damageTracker = new DamageTracker();

    @Unique
    private DamageTracker luahud$tracker() {
        luahud$damageTracker.bind(MinecraftClient.getInstance().player);
        return luahud$damageTracker;
    }

    // This call occurs after vanilla moves packet handling onto the client thread.
    @WrapOperation(method = "onEntityDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;onDamaged(Lnet/minecraft/entity/damage/DamageSource;)V"),
            require = 1, allow = 1)
    private void luahud$notifyLocalPlayerDamage(Entity entity, DamageSource source, Operation<Void> original) {
        original.call(entity, source);
        if (entity == MinecraftClient.getInstance().player) {
            luahud$tracker().onDamage(DamageEvent.fromNotification(source));
        }
    }

    @WrapOperation(method = "onHealthUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;updateHealth(F)V"), require = 1, allow = 1)
    private void luahud$measureHealthLoss(ClientPlayerEntity player, float health, Operation<Void> original) {
        original.call(player, health);
        luahud$tracker().onHealthUpdate(Math.max(0.0f, health));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void luahud$flushDamage(CallbackInfo ci) {
        DamageEvent event = luahud$tracker().tick();
        if (event != null) EventDispatcher.fireEvent("damage", event);
    }
}
