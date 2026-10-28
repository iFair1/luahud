package dev.ifair.luahud.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;

/** Damage to an entity, including absorption. Amounts are health points (two points per heart). */
public record DamageEvent(String type, Entity attacker, ItemStack weapon, double amount, Entity victim) {
    public DamageEvent(String type, Entity attacker, ItemStack weapon, double amount) {
        this(type, attacker, weapon, amount, null);
    }
    /** Copies the weapon so later equipment changes cannot alter a retained event. */
    public static DamageEvent fromNotification(Entity victim, DamageSource source) {
        String type = source.getTypeRegistryEntry().getKey().orElseThrow().getValue().toString();
        ItemStack weapon = source.getWeaponStack();
        return new DamageEvent(type, source.getAttacker(),
                weapon == null || weapon.isEmpty() ? null : weapon.copy(), 0.0, victim);
    }

    public DamageEvent withAmount(double amount) {
        return new DamageEvent(type, attacker, weapon, amount, victim);
    }
}
