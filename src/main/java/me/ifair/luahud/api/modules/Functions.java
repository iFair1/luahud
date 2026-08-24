package me.ifair.luahud.api.modules;

import com.mojang.brigadier.StringReader;
import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.models.BlockModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.util.math.BlockPos;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;


public class Functions implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        lh.set("getBlockAt", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue cords) {
                MinecraftClient client = MinecraftClient.getInstance();
                BlockPos pos = resolveCoordinates(cords.checkjstring(), client.player);

                return pos != null ? (new BlockModel(() -> client.world, () -> pos)).build() : LuaValue.NIL;
            }
        });
    }

    // I copied this method and don't know how does it work
    private BlockPos resolveCoordinates(String coordString, ClientPlayerEntity player) {
        try {
            StringReader reader = new StringReader(coordString);
            // Parses ~ and ^
            PosArgument posArgument = Vec3ArgumentType.vec3().parse(reader);

            // Make Vec3d thing
            return posArgument.toAbsoluteBlockPos(player.getCommandSource());
        } catch (Exception e) {
            LuaHUD.LOGGER.error("{} is not a valid position.", coordString);
            return null;
        }
    }
}
