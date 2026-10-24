package dev.ifair.luahud.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;

/** Damage to the local player. Amounts are health points (two points per heart). */
public record DamageEvent(String type, Entity attacker, ItemStack weapon, double amount) {
    /** Copies the weapon so later equipment changes cannot alter a retained event. */
    public static DamageEvent fromNotification(DamageSource source) {
        String type = source.getTypeRegistryEntry().getKey().orElseThrow().getValue().toString();
        ItemStack weapon = source.getWeaponStack();
        return new DamageEvent(type, source.getAttacker(),
                weapon == null || weapon.isEmpty() ? null : weapon.copy(), 0.0);
    }

    public DamageEvent withAmount(double amount) {
        return new DamageEvent(type, attacker, weapon, amount);
    }
}
