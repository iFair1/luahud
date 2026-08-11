package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.ApiModule;
import me.ifair.luahud.api.core.LazyTable;
import me.ifair.luahud.api.models.BlockModel;
import me.ifair.luahud.api.models.EntityModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.luaj.vm2.LuaTable;

public class FacingModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        MinecraftClient client = MinecraftClient.getInstance();

        LazyTable facing = new LazyTable();

        facing.addString("direction", () -> {
            return client.player != null ? client.player.getHorizontalFacing().asString() : "";
        });

        facing.addDouble("yaw", () -> client.player != null ? client.player.getYaw() : 0.0);
        facing.addDouble("pitch", () -> client.player != null ? client.player.getPitch() : 0.0);

        facing.addLazyTable("targetBlock", () -> {
            if (client.crosshairTarget instanceof BlockHitResult blockHit) {
                if (blockHit.getType() != HitResult.Type.MISS) {
                    return new BlockModel(() -> client.world, blockHit::getBlockPos).build();
                }
            }
            return null;
        });

        facing.addLazyTable("targetEntity", () -> {
            if (client.crosshairTarget instanceof EntityHitResult entityHit) {
                return new EntityModel(entityHit::getEntity).build();
            }
            return null;
        });
    }
}
