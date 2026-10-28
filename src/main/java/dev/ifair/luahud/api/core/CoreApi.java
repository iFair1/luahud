package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.BuiltinModels;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.models.DamageModel;
import dev.ifair.luahud.api.models.DeathModel;
import dev.ifair.luahud.api.models.ErrorModel;
import dev.ifair.luahud.api.modules.*;
import net.minecraft.client.MinecraftClient;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.error.ErrorEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;

/** Registers LuaHUD's built-in API using the same builders exposed to addons. */
public final class CoreApi {
    private CoreApi() {}

    public static void register(ApiRegistry registry) {
        register(registry, MinecraftClient.getInstance());
    }

    /** Registers metadata with deferred runtime bindings; the exporter passes no client. */
    static void register(ApiRegistry registry, MinecraftClient client) {
        LuaApiRegistrar api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        var death = DeathModel.register(api, models);

        RenderModule.register(api, models);
        PcModule.register(api, client);
        PerformanceModule.register(api, client);
        TimeModule.register(api, client);
        FacingModule.register(api, client, models);
        WeatherModule.register(api, client);
        SoundModule.register(api, client);
        PositionModule.register(api, client, models);
        PlayerModule.register(api, client, models, death);
        SlotsModule.register(api, client, models);
        SessionModule.register(api, client);
        FunctionsModule.register(api, client, models);
        ChatModule.register(api, client);
        DisplayModule.register(api, client);
        api.event("reload", "Runs once after active profiles load, including first world entry.");
        api.event("tick", "Runs at the end of each client tick.");
        api.event("render", "Runs immediately before each HUD frame is painted.");
        api.event("item-pickup", models.itemStack().type(),
                "Reports a server-confirmed ground item pickup by the local player. "
                        + "The ItemStack contains the picked-up count and preserves the item's components.");
        var damage = DamageModel.register(api, models);
        api.event("damage", damage.type(),
                "Reports local-player damage with observed health loss including absorption. Nearby updates are combined using the latest damage source; "
                        + "unmatched health loss uses minecraft:generic. Notification matching waits up to two client ticks.");
        api.event("attack", damage.type(),
                "Reports server-observed damage dealt by the local player, including projectiles. "
                        + "Uses the Damage model with victim and observed health/absorption loss, matched over two client ticks. "
                        + "Nearby hits on each victim are combined using the latest source; unavailable loss is 0.");
        api.event("kill", death.type(),
                "Reports a tracked living entity death whose latest client-observed damage source names the local player as attacker. "
                        + "Uses the Death model with victim; message is generated from the observed source. "
                        + "Limited to entities and damage sources visible to the client; this is not the server kill-credit statistic.");
        api.event("death", death.type(),
                "Reports the server's local-player death notification with position, dimension, death message and available damage source. "
                        + "Also runs when the death screen is disabled (immediate respawn).");
        var error = ErrorModel.register(api);
        api.event("error", error.type(),
                "Reports each recorded error occurrence on the next client lifecycle tick, after profiles load. "
                        + "Repeated errors keep their ID and increment count; isNew is true only for the first occurrence. "
                        + "Errors raised by error handlers are recorded without producing another error event.");
        registry.addEventFunctions();
        // LuaType exposes model metadata, so freeze definitions before sharing them with addons.
        registry.models().forEach(ModelBuilder::freeze);
        registry.builtinModels(new ModelTypes(models.block().type(), models.entity().type(), models.itemStack().type(),
                models.statusEffect().type(), models.horse().type(), damage.type(), death.type(), error.type()));
    }

    private record ModelTypes(
            LuaType<CoreModels.BlockView> block,
            LuaType<Entity> entity,
            LuaType<ItemStack> itemStack,
            LuaType<StatusEffectInstance> statusEffect,
            LuaType<AbstractHorseEntity> horse,
            LuaType<DamageEvent> damage,
            LuaType<DeathEvent> death,
            LuaType<ErrorEntry> error
    ) implements BuiltinModels {}
}
