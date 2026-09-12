package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.ModelBuilder;
import me.ifair.luahud.api.models.CoreModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;

public final class PositionModule {
    private PositionModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        ModelBuilder<CoreModels.BlockView> block = models.block();
        api.root().namespace("pos", pos -> {
            pos.namespace("biome", biome -> biome
                    .string("id", "Namespaced biome ID", () -> client.world == null || client.player == null
                            ? "" : client.world.getBiome(client.player.getBlockPos()).getIdAsString())
                    .string("name", "Localized biome name", () -> biomeName(client)));

            pos.namespace("chunk", chunk -> chunk
                    .bool("isSlimeChunk", "Whether the chunk is a slime chunk", () -> isSlimeChunk(client)));

            pos.string("dimension", "Namespaced dimension ID", () -> client.world.getRegistryKey().getValue().toString())
                    .model("highestBlock", block, "Highest block at player coordinates", () -> getHighestBlock(client));
        });
    }

    private static String biomeName(MinecraftClient client) {
        if (client.world == null || client.player == null) return "";
        return client.world.getBiome(client.player.getBlockPos()).getKey()
                .map(key -> Text.translatable(Util.createTranslationKey("biome", key.getValue())).getString())
                .orElse("");
    }

    private static boolean isSlimeChunk(MinecraftClient client) {
        if (client.getServer() == null) return false;

        ChunkPos pos = client.player.getChunkPos();

        long seed = client.getServer()
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();

        return ChunkRandom.getSlimeRandom(
            pos.x,
            pos.z,
            seed,
            987234911L // magic number that is used for slime chunks
        ).nextInt(10) == 0;
    }

    private static CoreModels.BlockView getHighestBlock(MinecraftClient client) {
        BlockPos pos = client.world.getTopPosition(
                Heightmap.Type.WORLD_SURFACE,
                client.player.getBlockPos()
        ).down();

        return new CoreModels.BlockView(client.world, pos);
    }
}
