package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.core.DeathEvent;

/** Registers the local-player death event payload. */
public final class DeathModel {
    private DeathModel() {}

    public static ModelBuilder<DeathEvent> register(LuaApiRegistrar api, CoreModels models) {
        ModelBuilder<DeathEvent> model = api.model("Death", DeathEvent.class);
        model.number("x", "Player X coordinate at death", DeathEvent::x);
        model.number("y", "Player Y coordinate at death", DeathEvent::y);
        model.number("z", "Player Z coordinate at death", DeathEvent::z);
        model.string("dimension", "Namespaced dimension ID at death", DeathEvent::dimension);
        model.string("message", "Server-provided death message as plain text", DeathEvent::message);
        model.string("damageType", "Latest client-observed damage type ID; minecraft:generic when unavailable. May differ from the server's fatal source.",
                DeathEvent::type);
        model.optionalField("attacker", models.entity().type(), "Attacker from the latest client-observed damage source; nil when unavailable",
                DeathEvent::attacker);
        model.optionalField("weapon", models.itemStack().type(), "Weapon copied from the available damage source at death; nil for an empty hand or unavailable weapon",
                DeathEvent::weapon);
        return model;
    }
}
