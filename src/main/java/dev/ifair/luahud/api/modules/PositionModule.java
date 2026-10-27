package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.api.models.CoreModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
//? if >=1.21.11 {
/*import net.minecraft.world.attribute.EnvironmentAttributes;
*///?}

public final class PositionModule {
    private PositionModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        ModelBuilder<CoreModels.BlockView> block = models.block();
        api.root().namespace("pos", pos -> {
            pos.namespace("biome", biome -> biome
                    .string("id", "Namespaced biome ID", () -> client.world.getBiome(client.player.getBlockPos()).getIdAsString())
                    .string("name", "Localized biome name", () -> biomeName(client))
                    .string("precipitation", "Biome precipitation type", () -> String.valueOf(precipitation(client)))
                    .number("temperature", "Biome temperature",  () -> (double) biome(client).getTemperature())
                    .integer("waterColor", "Water color of the biome", () -> biome(client).getWaterColor())
                    .integer("skyColor", "Sky color of the biome", () -> skyColor(client))
                    .integer("foliageColor", "Foliage color of the biome", () -> biome(client).getFoliageColor())
                    .integer("grassColor", "Grass color of the biome", () -> biome(client).getGrassColorAt(client.player.getBlockX(), client.player.getBlockZ())));

            pos.namespace("chunk", chunk -> chunk
                    .bool("isSlimeChunk", "Whether the chunk is a slime chunk", () -> isSlimeChunk(client))
                    .integer("icx", "In chunk x coordinate", () -> client.player.getBlockX() & 15)
                    .integer("icy", "In chunk y coordinate", () -> client.player.getBlockY() & 15)
                    .integer("icz", "In chunk z coordinate", () -> client.player.getBlockZ() & 15));

            pos.string("dimension", "Namespaced dimension ID", () -> client.world.getRegistryKey().getValue().toString())
                    .model("highestBlock", block, "Highest block at player coordinates", () -> getHighestBlock(client));
        });
    }

    private static Biome biome(MinecraftClient client) {
        return client.world.getBiome(client.player.getBlockPos()).value();
    }

    private static Biome.Precipitation precipitation(MinecraftClient client) {
        //? if <1.21.2 {
        return biome(client).getPrecipitation(client.player.getBlockPos());
        //?} else {
        /*return biome(client).getPrecipitation(client.player.getBlockPos(), client.world.getSeaLevel());
        *///?}
    }

    private static int skyColor(MinecraftClient client) {
        //? if <1.21.11 {
        return biome(client).getSkyColor();
        //?} else {
        /*var attribute = EnvironmentAttributes.SKY_COLOR_VISUAL;
        return biome(client).getEnvironmentAttributes().apply(attribute, attribute.getDefaultValue());
        *///?}
    }

    private static String biomeName(MinecraftClient client) {
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
