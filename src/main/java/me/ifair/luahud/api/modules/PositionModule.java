package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.world.biome.Biome;
import org.luaj.vm2.LuaTable;

public class PositionModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LazyTable pos = new LazyTable();

        LazyTable biome = new LazyTable();
        biome.addString("id", () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return "";
            }

            return client.world.getBiome(client.player.getBlockPos()).getIdAsString();
        });
        biome.addString("name", () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return "";
            }

            return Text.translatable(Util.createTranslationKey("biome", client.world.getBiome(client.player.getBlockPos()).getKey().get().getValue())).getString();
        });

        pos.addTable("biome", biome.build());

        LazyTable dimension = new LazyTable();
        dimension.addString("id", () -> {
            ClientWorld world = MinecraftClient.getInstance().world;
            if (world == null) return "";

            return world.getRegistryKey().getValue().toString();
        });

        pos.addTable("dimension", dimension.build());

        lh.set("pos", pos.build());
    }

    private RegistryEntry<Biome> getBiomeEntry() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.player == null) {
            return null;
        }

        return client.world.getBiome(client.player.getBlockPos());
    }
}
