package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.models.DamageModel;
import dev.ifair.luahud.api.models.DeathModel;
import dev.ifair.luahud.api.models.ErrorModel;
import dev.ifair.luahud.api.modules.*;
import net.minecraft.client.MinecraftClient;

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
        api.event("damage", DamageModel.register(api, models).type(),
                "Reports local-player damage with observed health loss including absorption. Nearby updates are combined using the latest damage source; "
                        + "unmatched health loss uses minecraft:generic. Notification matching waits up to two client ticks.");
        api.event("death", death.type(),
                "Reports the server's local-player death notification with position, dimension, death message and available damage source. "
                        + "Also runs when the death screen is disabled (immediate respawn).");
        api.event("error", ErrorModel.register(api).type(),
                "Reports each recorded error occurrence on the next client lifecycle tick, after profiles load. "
                        + "Repeated errors keep their ID and increment count; isNew is true only for the first occurrence. "
                        + "Errors raised by error handlers are recorded without producing another error event.");
        registry.addEventFunctions();
    }
}
