package dev.ifair.luahud.core;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;

/** Entity death with a snapshot of the position and available damage source. */
public record DeathEvent(double x, double y, double z, String dimension, String message,
                         String type, Entity attacker, ItemStack weapon, Entity victim) {
    public DeathEvent(double x, double y, double z, String dimension, String message,
                      String type, Entity attacker, ItemStack weapon) {
        this(x, y, z, dimension, message, type, attacker, weapon, null);
    }
    public static DeathEvent fromNotification(LivingEntity player, String dimension, String message) {
        DamageSource source = player.getRecentDamageSource();
        DamageEvent damage = source == null
                ? new DamageEvent("minecraft:generic", null, null, 0.0)
                : DamageEvent.fromNotification(player, source);
        return new DeathEvent(player.getX(), player.getY(), player.getZ(), dimension, message,
                damage.type(), damage.attacker(), damage.weapon(), player);
    }
}
