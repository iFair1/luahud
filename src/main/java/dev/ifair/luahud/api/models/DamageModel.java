package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.core.DamageEvent;

/** Registers the local-player damage event payload. */
public final class DamageModel {
    private DamageModel() {}

    public static ModelBuilder<DamageEvent> register(LuaApiRegistrar api, CoreModels models) {
        ModelBuilder<DamageEvent> model = api.model("Damage", DamageEvent.class);
        model.string("type", "Namespaced damage type ID, such as minecraft:fall or minecraft:player_attack", DamageEvent::type);
        model.optionalField("attacker", models.entity().type(), "Attacking entity; nil for environmental damage or an unavailable attacker",
                DamageEvent::attacker);
        model.optionalField("weapon", models.itemStack().type(), "Weapon stack observed at damage notification time; nil for an empty hand or unavailable weapon",
                DamageEvent::weapon);
        model.number("amount", "Observed health loss including absorption in health points (two per heart), grouped with nearby damage notifications; "
                + "0 if no loss was observed. Absorption loss requires a damage notification; may combine multiple hits.", DamageEvent::amount);
        return model;
    }
}
