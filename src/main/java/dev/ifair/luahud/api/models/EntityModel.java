package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.registry.Registries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class EntityModel {
    static void register(ModelBuilder<Entity> model, ModelBuilder<StatusEffectInstance> statusEffect,
                         ModelBuilder<AbstractHorseEntity> horse) {
        model.field("id", RegistryIds.ENTITY_TYPE, "Namespaced entity type", value -> Registries.ENTITY_TYPE.getId(value.getType()).toString());
        model.number("x", "X coordinate", Entity::getX);
        model.number("y", "Y coordinate", Entity::getY);
        model.number("z", "Z coordinate", Entity::getZ);
        model.string("name", "Localized display name", value -> value.getDisplayName() == null ? "" : value.getDisplayName().getString());
        var uuid = model.string("uuid", "World entity UUID", Entity::getUuidAsString).inputRequired();
        model.integer("health", "Current health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getHealth() : 0);
        model.integer("maxHealth", "Maximum health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getMaxHealth() : 0);
        model.field("effects", LuaType.map(RegistryIds.STATUS_EFFECT, statusEffect.type()), "Active status effects keyed by namespaced ID", EntityModel::statusEffects);
        model.bool("isLiving", "Whether the entity is a living entity", value -> value instanceof LivingEntity);
        model.bool("isBaby", "Whether the entity is a baby", value -> value instanceof LivingEntity living && living.isBaby());
        model.optionalField("horse", horse.type(), "Horse-family properties, or nil for other entities", HorseModel::horse);
        model.fromFields(fields -> getEntityBy(fields.get(uuid)));
    }

    static Entity getEntityBy(String uuidValue) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidValue);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid entity UUID: " + uuidValue, error);
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) throw new IllegalStateException("Cannot use an entity model without a loaded world");
        for (Entity candidate : world.getEntities()) {
            if (candidate.getUuid().equals(uuid)) return candidate;
        }
        throw new IllegalArgumentException("No loaded entity found for UUID: " + uuidValue);
    }

    public static Map<String, StatusEffectInstance> statusEffects(Entity entity) {
        Map<String, StatusEffectInstance> result = new LinkedHashMap<>();
        if (!(entity instanceof LivingEntity living)) return result;

        for (StatusEffectInstance effect : living.getStatusEffects()) {
            result.put(Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString(), effect);
        }
        return result;
    }
}
