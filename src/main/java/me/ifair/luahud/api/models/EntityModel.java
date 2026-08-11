package me.ifair.luahud.api.models;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;

import java.util.function.Supplier;

public class EntityModel extends PositionModel {
    public EntityModel(Supplier<Entity> entitySupplier) {
        super(() -> {
            Entity entity = entitySupplier.get();
            return entity != null ? entity.getPos() : null;
        });

        addString("id", () -> {
            Entity entity = entitySupplier.get();
            return entity != null ? Registries.ENTITY_TYPE.getId(entity.getType()).toString() : "";
        });

        addString("name", () -> {
            Entity entity = entitySupplier.get();
            return (entity != null && entity.getDisplayName() != null)
                    ? entity.getDisplayName().getString()
                    : "";
        });

        addString("uuid", () -> {
            Entity entity = entitySupplier.get();
            return entity != null ? entity.getUuidAsString() : "";
        });

        addDouble("health", () -> {
            Entity entity = entitySupplier.get();
            return (entity instanceof LivingEntity living) ? living.getHealth() : 0.0;
        });

        addDouble("maxHealth", () -> {
            Entity entity = entitySupplier.get();
            return (entity instanceof LivingEntity living) ? living.getMaxHealth() : 0.0;
        });
    }
}
