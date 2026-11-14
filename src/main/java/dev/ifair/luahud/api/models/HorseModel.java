package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;

public final class HorseModel {
    static ModelBuilder<AbstractHorseEntity> register(LuaApiRegistrar api, ModelBuilder<Entity> entity) {
        ModelBuilder<AbstractHorseEntity> model = api.model("Horse", AbstractHorseEntity.class);
        model.number("movementSpeed", "Effective movement-speed attribute (not blocks per second)",
                //? if <1.21.2 {
                value -> value.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                //?} else {
                /*value -> value.getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
                *///?}
        model.number("jumpStrength", "Effective jump-strength attribute (not jump height)",
                //? if <1.21.2 {
                value -> value.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
                //?} else {
                /*value -> value.getAttributeValue(EntityAttributes.JUMP_STRENGTH));
                *///?}
        //? if <1.21.5 {
        model.bool("isSaddled", "Whether a saddle is equipped", value -> value.isSaddled());
        //?} else {
        /*model.bool("isSaddled", "Whether a saddle is equipped", value -> !value.getEquippedStack(net.minecraft.entity.EquipmentSlot.SADDLE).isEmpty());
        *///?}
        model.bool("isTamed", "Whether the animal is tamed", value -> value.isTame());
        model.optionalField("rider", entity.type(), "First passenger, or nil when unoccupied", Entity::getFirstPassenger);
        model.bool("hasChest", "Whether a chest is attached", value -> value instanceof AbstractDonkeyEntity donkey && donkey.hasChest());
        return model;
    }

    public static AbstractHorseEntity horse(Entity entity) {
        return entity instanceof AbstractHorseEntity horse ? horse : null;
    }
}
