package me.ifair.luahud.api.modules.pc;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;

public class DisplayProvider {
    public LuaTable build() {
        MinecraftClient client = MinecraftClient.getInstance();
        LazyTable display = new LazyTable();

        display.addInt("width", () -> client.getWindow().getWidth());
        display.addInt("height", () -> client.getWindow().getHeight());
        display.addInt("scale", () -> (int) client.getWindow().getScaleFactor());
    }
}
