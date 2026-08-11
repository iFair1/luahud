package me.ifair.luahud.api.models;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.network.ClientPlayerEntity;
import org.luaj.vm2.LuaTable;

import java.util.function.Supplier;

public class PlayerModel extends EntityModel {
    public PlayerModel(Supplier<ClientPlayerEntity> playerSupplier) {
        super(playerSupplier::get);

        addString("username", () -> {
            ClientPlayerEntity player = playerSupplier.get();
            return player != null ? player.getGameProfile().getName() : "";
        });

        addBoolean("sprinting", () -> {
            ClientPlayerEntity player = playerSupplier.get();
            return player != null && player.isSprinting();
        });

        addLazyTable("hunger", () -> buildHunger(playerSupplier));
    }

    private static LuaTable buildHunger(Supplier<ClientPlayerEntity> playerSupplier) {
        LazyTable hunger = new LazyTable();
        hunger.addInt("food", () -> {
            ClientPlayerEntity player = playerSupplier.get();
            return player != null ? player.getHungerManager().getFoodLevel() : 0;
        });
        hunger.addDouble("saturation", () -> {
            ClientPlayerEntity player = playerSupplier.get();
            return player != null ? player.getHungerManager().getSaturationLevel() : 0.0f;
        });
        return hunger.build();
    }
}
