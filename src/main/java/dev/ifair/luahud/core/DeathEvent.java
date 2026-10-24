package dev.ifair.luahud.core;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;

/** Local-player death with a snapshot of the position and available damage source. */
public record DeathEvent(double x, double y, double z, String dimension, String message,
                         String type, Entity attacker, ItemStack weapon) {
    public static DeathEvent fromNotification(ClientPlayerEntity player, String dimension, String message) {
        DamageSource source = player.getRecentDamageSource();
        DamageEvent damage = source == null
                ? new DamageEvent("minecraft:generic", null, null, 0.0)
                : DamageEvent.fromNotification(source);
        return new DeathEvent(player.getX(), player.getY(), player.getZ(), dimension, message,
                damage.type(), damage.attacker(), damage.weapon());
    }
}
