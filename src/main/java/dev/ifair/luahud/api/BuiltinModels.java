package dev.ifair.luahud.api;

import dev.ifair.luahud.api.models.CoreModels.BlockView;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.error.ErrorEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;

/**
 * LuaHUD's registered, frozen model types. Reuse these types for addon values,
 * model fields, function signatures, and event payloads without registering copies.
 */
public interface BuiltinModels {
    LuaType<BlockView> block();
    LuaType<Entity> entity();
    LuaType<ItemStack> itemStack();
    LuaType<StatusEffectInstance> statusEffect();
    LuaType<AbstractHorseEntity> horse();
    LuaType<DamageEvent> damage();
    LuaType<DeathEvent> death();
    LuaType<ErrorEntry> error();
}
