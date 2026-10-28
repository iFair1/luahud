package dev.ifair.luahud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DamageTracker;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.core.EventDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientAttackMixin {
    @Unique private final Map<LivingEntity, DamageTracker> luahud$targets = new IdentityHashMap<>();
    @Unique private final Set<Entity> luahud$reportedKills = Collections.newSetFromMap(new IdentityHashMap<>());
    @Unique private Object luahud$world;
    @Unique private Object luahud$player;

    @Unique
    private void luahud$bindCombat() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (luahud$world != client.world || luahud$player != client.player) {
            luahud$targets.clear();
            luahud$reportedKills.clear();
            luahud$world = client.world;
            luahud$player = client.player;
        }
    }

    @Unique
    private DamageTracker luahud$target(LivingEntity victim) {
        luahud$bindCombat();
        return luahud$targets.computeIfAbsent(victim, entity -> {
            DamageTracker tracker = new DamageTracker();
            tracker.bind(entity);
            tracker.onHealthUpdate(Math.max(0.0f, entity.getHealth()));
            return tracker;
        });
    }

    @WrapOperation(method = "onEntityDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;onDamaged(Lnet/minecraft/entity/damage/DamageSource;)V"),
            require = 1, allow = 1)
    private void luahud$observeAttack(Entity entity, DamageSource source, Operation<Void> original) {
        original.call(entity, source);
        if (entity instanceof LivingEntity victim && entity != MinecraftClient.getInstance().player) {
            // Keep other attackers' notifications too, so their damage cannot inherit our attribution.
            luahud$target(victim).onDamage(DamageEvent.fromNotification(victim, source));
        }
    }

    @WrapOperation(method = "onEntityTrackerUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V"),
            require = 1, allow = 1)
    private void luahud$measureTargetLoss(DataTracker dataTracker, List<DataTracker.SerializedEntry<?>> entries,
                                        Operation<Void> original, EntityTrackerUpdateS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = client.world == null ? null : client.world.getEntityById(packet.id());
        LivingEntity victim = entity instanceof LivingEntity living && entity != client.player ? living : null;
        DamageTracker tracker = victim == null ? null : luahud$target(victim);
        float absorption = victim == null ? 0.0f : victim.getAbsorptionAmount();
        original.call(dataTracker, entries);
        if (tracker != null) {
            tracker.onHealthUpdate(Math.max(0.0f, victim.getHealth()));
            tracker.onAbsorptionUpdate(absorption, victim.getAbsorptionAmount());
        }
    }

    @WrapOperation(method = "onEntityStatus", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;handleStatus(B)V"), require = 1, allow = 1)
    private void luahud$observeKill(Entity entity, byte status, Operation<Void> original) {
        luahud$bindCombat();
        MinecraftClient client = MinecraftClient.getInstance();
        DeathEvent death = null;
        if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                && entity instanceof LivingEntity victim && entity != client.player && client.player != null) {
            DamageSource source = victim.getRecentDamageSource();
            if (source != null && source.getAttacker() == client.player && luahud$reportedKills.add(victim)) {
                death = DeathEvent.fromNotification(victim, client.world.getRegistryKey().getValue().toString(),
                        source.getDeathMessage(victim).getString());
                // Death status may precede the final health metadata, especially for remote players.
                luahud$target(victim).onHealthUpdate(0.0f);
            }
        }
        original.call(entity, status);
        if (death != null) EventDispatcher.fireEvent("kill", death);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void luahud$flushAttacks(CallbackInfo ci) {
        luahud$bindCombat();
        MinecraftClient client = MinecraftClient.getInstance();
        luahud$targets.entrySet().removeIf(entry -> {
            DamageEvent event = entry.getValue().tick();
            if (event != null && client.player != null && event.attacker() == client.player) {
                EventDispatcher.fireEvent("attack", event);
            }
            return entry.getKey().isRemoved() && entry.getValue().isIdle();
        });
        luahud$reportedKills.removeIf(Entity::isRemoved);
    }
}
