package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.models.CoreModels;
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

        RenderModule.register(api, models);
        PcModule.register(api, client);
        PerformanceModule.register(api, client);
        TimeModule.register(api, client);
        FacingModule.register(api, client, models);
        WeatherModule.register(api, client);
        SoundModule.register(api, client);
        PositionModule.register(api, client, models);
        PlayerModule.register(api, client, models);
        SlotsModule.register(api, client, models);
        SessionModule.register(api, client);
        FunctionsModule.register(api, client, models);
        registry.addEventFunctions();
    }
}
