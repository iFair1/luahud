package dev.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DamageTracker;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.core.DeathState;
import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientDamageMixin implements DeathState {
    @Unique private final DamageTracker luahud$damageTracker = new DamageTracker();
    @Unique private DeathEvent luahud$lastDeath;

    @Override
    public DeathEvent luahud$getLastDeath() {
        return luahud$lastDeath;
    }

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
            luahud$tracker().onDamage(DamageEvent.fromNotification(entity, source));
        }
    }

    @WrapOperation(method = "onHealthUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;updateHealth(F)V"), require = 1, allow = 1)
    private void luahud$measureHealthLoss(ClientPlayerEntity player, float health, Operation<Void> original) {
        original.call(player, health);
        luahud$tracker().onHealthUpdate(Math.max(0.0f, health));
    }

    @WrapOperation(method = "onEntityTrackerUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V"),
            require = 1, allow = 1)
    private void luahud$measureAbsorptionLoss(DataTracker dataTracker, List<DataTracker.SerializedEntry<?>> entries,
                                            Operation<Void> original) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        boolean localPlayer = player != null && dataTracker == player.getDataTracker();
        float previous = localPlayer ? player.getAbsorptionAmount() : 0.0f;
        original.call(dataTracker, entries);
        if (localPlayer) {
            luahud$tracker().onAbsorptionUpdate(previous, player.getAbsorptionAmount());
        }
    }

    // TAIL runs after vanilla's client-thread check, including the immediate-respawn path.
    @Inject(method = "onDeathMessage", at = @At("TAIL"))
    private void luahud$notifyLocalPlayerDeath(DeathMessageS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player != null && client.world != null && packet.playerId() == player.getId()) {
            luahud$lastDeath = DeathEvent.fromNotification(player,
                    client.world.getRegistryKey().getValue().toString(), packet.message().getString());
            EventDispatcher.fireEvent("death", luahud$lastDeath);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void luahud$flushDamage(CallbackInfo ci) {
        DamageEvent event = luahud$tracker().tick();
        if (event != null) EventDispatcher.fireEvent("damage", event);
    }
}
