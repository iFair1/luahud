package me.ifair.luahud.api.core;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.api.modules.FacingModule;
import me.ifair.luahud.api.modules.FunctionsModule;
import me.ifair.luahud.api.modules.PcModule;
import me.ifair.luahud.api.modules.PerformanceModule;
import me.ifair.luahud.api.modules.PlayerModule;
import me.ifair.luahud.api.modules.PositionModule;
import me.ifair.luahud.api.modules.RenderModule;
import me.ifair.luahud.api.modules.TimeModule;
import me.ifair.luahud.api.modules.WeatherModule;
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
        PositionModule.register(api, client);
        PlayerModule.register(api, client, models.itemStack());
        FunctionsModule.register(api, client, models);
        registry.addEventFunctions();
    }
}
