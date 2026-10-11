package dev.ifair.luahud.compat;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

/** Resolves vanilla coordinates without requiring an integrated server. */
public final class ClientCoordinates {
    private ClientCoordinates() {}

    public static BlockPos resolve(String value, Vec3d position, Vec2f rotation) {
        try {
            PosArgument argument = Vec3ArgumentType.vec3().parse(new StringReader(value));
            // Coordinate resolution only reads position, rotation and the feet anchor.
            // The world, server and entity may be absent on a multiplayer client.
            ServerCommandSource source = new ServerCommandSource(CommandOutput.DUMMY, position, rotation, null,
                    //? if <1.21.11 {
                    0,
                    //?} else {
                    /*net.minecraft.command.permission.PermissionPredicate.NONE,
                    *///?}
                    "LuaHUD", Text.empty(), null, null);
            return argument.toAbsoluteBlockPos(source);
        } catch (Exception error) {
            throw new IllegalArgumentException(value + " is not a valid position", error);
        }
    }
}
