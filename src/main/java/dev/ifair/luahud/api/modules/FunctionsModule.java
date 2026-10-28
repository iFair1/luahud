package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.FunctionBuilder;
import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.compat.ClientCoordinates;
import dev.ifair.luahud.api.models.CoreModels.BlockView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class FunctionsModule {
    private FunctionsModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        FunctionBuilder<String> getTranslation = api.root().function(
                "getTranslation", LuaType.string(),
                "Translate a key using the current client language. Returns the key if no translation exists.");
        LuaParameter<String> key = getTranslation.parameter("key", LuaType.string(), "Translation key");
        getTranslation.call(arguments -> Text.translatable(arguments.get(key)).getString());

        FunctionBuilder<BlockView> getBlock = api.root().function(
                "getBlockAt", LuaType.optional(models.block().type()), "Get the block at absolute, ~, or ^ coordinates.");
        LuaParameter<String> coordinate = getBlock.parameter("coordinate", LuaType.string(), "Minecraft coordinate expression");
        getBlock.call(arguments -> {
            BlockPos pos = resolveCoordinates(arguments.get(coordinate), client.player);
            return pos == null ? null : new BlockView(client.world, pos);
        });
    }

    private static BlockPos resolveCoordinates(String value, ClientPlayerEntity player) {
        return ClientCoordinates.resolve(value,
                new Vec3d(player.getX(), player.getY(), player.getZ()),
                player.getRotationClient());
    }
}
