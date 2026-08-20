package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.models.PlayerModel;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;

public class PlayerModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Doing this once because PlayerModule always looks at client.player
        PlayerModel playerModel = new PlayerModel(() -> client.player);

        lh.set("player", playerModel.build());
    }
}
