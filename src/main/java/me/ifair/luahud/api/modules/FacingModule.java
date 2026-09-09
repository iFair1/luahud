package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.api.models.CoreModels.BlockView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

public final class FacingModule {
    private FacingModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        api.root().namespace("facing", facing -> facing
                .string("direction", "Horizontal facing direction", () -> client.player == null ? "" : client.player.getHorizontalFacing().asString())
                .number("yaw", "Wrapped yaw in degrees", () -> client.player == null ? 0.0 : (double) MathHelper.wrapDegrees(client.player.getYaw()))
                .number("pitch", "Pitch in degrees", () -> client.player == null ? 0.0 : (double) client.player.getPitch())
                .value("targetBlock", LuaType.optional(models.block().type()), LuaDoc.of("Block under the crosshair"), () -> targetBlock(client))
                .value("targetEntity", LuaType.optional(models.entity().type()), LuaDoc.of("Entity under the crosshair"), () -> targetEntity(client)));
    }

    private static BlockView targetBlock(MinecraftClient client) {
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
            return new BlockView(client.world, hit.getBlockPos());
        }
        return null;
    }

    private static Entity targetEntity(MinecraftClient client) {
        return client.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
    }
}
