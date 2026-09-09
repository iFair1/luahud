package me.ifair.luahud.api.modules;

import com.mojang.brigadier.StringReader;
import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.FunctionBuilder;
import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaParameter;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.api.models.CoreModels.BlockView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.util.math.BlockPos;

public final class FunctionsModule {
    private FunctionsModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        FunctionBuilder<BlockView> getBlock = api.root().function(
                "getBlockAt", LuaType.optional(models.block().type()), "Get the block at absolute, ~, or ^ coordinates.");
        LuaParameter<String> coordinate = getBlock.parameter("coordinate", LuaType.string(), "Minecraft coordinate expression");
        getBlock.call(arguments -> {
            BlockPos pos = resolveCoordinates(arguments.get(coordinate), client.player);
            return pos == null ? null : new BlockView(client.world, pos);
        });
    }

    private static BlockPos resolveCoordinates(String value, ClientPlayerEntity player) {
        try {
            PosArgument argument = Vec3ArgumentType.vec3().parse(new StringReader(value));
            return player == null ? null : argument.toAbsoluteBlockPos(player.getCommandSource());
        } catch (Exception error) {
            LuaHUD.LOGGER.error("{} is not a valid position.", value);
            return null;
        }
    }
}
