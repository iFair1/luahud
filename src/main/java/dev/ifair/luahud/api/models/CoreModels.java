package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/** Shared model descriptors used by LuaHUD's built-in modules. */
public record CoreModels(
        ModelBuilder<BlockView> block,
        ModelBuilder<Entity> entity,
        ModelBuilder<ItemStack> itemStack,
        ModelBuilder<StatusEffectInstance> statusEffect,
        ModelBuilder<AbstractHorseEntity> horse,
        ModelBuilder<ColorModel.Color> color
) {
    public record BlockView(ClientWorld world, BlockPos pos) {}

    public static CoreModels register(LuaApiRegistrar api) {
        ModelBuilder<StatusEffectInstance> statusEffect = StatusEffectModel.register(api);
        ModelBuilder<Entity> entity = api.model("Entity", Entity.class);
        ModelBuilder<AbstractHorseEntity> horse = HorseModel.register(api, entity);
        EntityModel.register(entity, statusEffect, horse);

        ModelBuilder<BlockView> block = PlacedBlockModel.register(api);

        return new CoreModels(block, entity, ItemStackModel.register(api), statusEffect, horse, ColorModel.register(api));
    }
}
