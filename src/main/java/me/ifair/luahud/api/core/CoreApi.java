package me.ifair.luahud.api.core;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.api.modules.*;
import net.minecraft.client.MinecraftClient;

/** Registers LuaHUD's built-in API using the same builders exposed to addons. */
public final class CoreApi {
    private CoreApi() {}

    public static void register(ApiRegistry registry) {
        LuaApiRegistrar api = registry.registrar("luahud");
        MinecraftClient client = MinecraftClient.getInstance();
        CoreModels models = CoreModels.register(api);

        RenderModule.register(api, models);
        PcModule.register(api, client);
        PerformanceModule.register(api, client);
        TimeModule.register(api, client);
        FacingModule.register(api, client, models);
        WeatherModule.register(api, client);
        PositionModule.register(api, client, models);
        PlayerModule.register(api, client, models);
        SlotsModule.register(api, client, models);
        SessionModule.register(api, client);
        FunctionsModule.register(api, client, models);
        registry.addEventFunctions();
    }
}
