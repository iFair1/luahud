package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;

import static dev.ifair.luahud.api.models.ModelRegistryValues.*;

public final class StatusEffectModel {
    static ModelBuilder<StatusEffectInstance> register(LuaApiRegistrar api) {
        ModelBuilder<StatusEffectInstance> model = api.model("StatusEffect", StatusEffectInstance.class);
        var id = model.field("id", RegistryIds.STATUS_EFFECT, "Namespaced status effect ID",
                value -> Registries.STATUS_EFFECT.getId(value.getEffectType().value()).toString()).inputRequired();
        model.string("name", "Localized effect name", value -> value.getEffectType().value().getName().getString());
        var level = model.integer("level", "Effect level; defaults to 1 for input tables",
                value -> value.getAmplifier() + 1).inputDefault(1);
        var seconds = model.integer("durationInSeconds", "Remaining duration in seconds (-1 if infinite)",
                effect -> durationInSeconds(effect.getDuration())).inputOptional();
        var ticks = model.integer("durationInTicks", "Remaining duration in ticks (-1 if infinite); takes precedence over seconds for input tables",
                StatusEffectInstance::getDuration).inputOptional();
        var showParticles = model.bool("showParticles", "Whether effect particles are shown; defaults to true for input tables", StatusEffectInstance::shouldShowParticles).inputDefault(true);
        model.bool("isAmbient", "Whether the source is a beacon or a conduit", StatusEffectInstance::isAmbient).inputDefault(false);
        model.fromFields(fields -> {
            StatusEffect effect = registryValue(Registries.STATUS_EFFECT, fields.get(id), "status effect");
            Integer durationTicks;
            if (fields.isExplicit(ticks)) {
                durationTicks = fields.get(ticks);
            } else if (fields.isExplicit(seconds)) {
                durationTicks = durationInTicks(fields.get(seconds));
            } else {
                durationTicks = fields.get(ticks);
                if (durationTicks == null) {
                    Integer durationSeconds = fields.get(seconds);
                    durationTicks = durationSeconds == null ? 0 : durationInTicks(durationSeconds);
                }
            }
            int effectLevel = fields.get(level);
            if (effectLevel < 1) throw new IllegalArgumentException("Effect level must be at least 1");
            if (durationTicks < StatusEffectInstance.INFINITE) throw new IllegalArgumentException("Effect duration must be -1 or non-negative");
            StatusEffectInstance source = fields.source();
            return new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(effect), durationTicks, effectLevel - 1,
                    source == null || source.isAmbient(), fields.get(showParticles),
                    source == null || source.shouldShowIcon());
        });
        return model;
    }

    static int durationInTicks(int seconds) {
        return seconds == StatusEffectInstance.INFINITE ? StatusEffectInstance.INFINITE : Math.multiplyExact(seconds, 20);
    }

    static int durationInSeconds(int ticks) {
        return ticks == StatusEffectInstance.INFINITE ? StatusEffectInstance.INFINITE : ticks / 20;
    }
}
