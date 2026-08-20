package me.ifair.luahud.api.models;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.world.GameMode;
import org.luaj.vm2.LuaTable;

import java.util.Locale;
import java.util.function.Supplier;

public class PlayerModel extends EntityModel {
    public PlayerModel(Supplier<ClientPlayerEntity> playerSupplier) {
        super(playerSupplier::get);

        addString("username", () -> playerSupplier.get().getGameProfile().getName());

        addBoolean("sprinting", () -> playerSupplier.get().isSprinting());

        // TODO: sprintToggled

        addTable("hunger", buildHunger(playerSupplier));

        addTable("armor", buildArmor(playerSupplier));

        addTable("xp", buildXp(playerSupplier));

        addString("gamemode", () -> {
            PlayerListEntry entry = playerSupplier.get().networkHandler.getPlayerListEntry(playerSupplier.get().getUuid());

            if (entry == null) return "";

            GameMode gameMode = entry.getGameMode();
            return gameMode.name().toLowerCase(Locale.ROOT); // Locale.ROOT because Turkish İ Problem 🇹🇷🇹🇷
        });
    }

    private static LuaTable buildHunger(Supplier<ClientPlayerEntity> playerSupplier) {
        LazyTable hunger = new LazyTable();

        hunger.addDouble("food", () -> playerSupplier.get().getHungerManager().getFoodLevel());
        hunger.addDouble("saturation", () -> playerSupplier.get().getHungerManager().getSaturationLevel());
        // Cannot get exhaustion from client without estimating.

        return hunger.build();
    }

    private static LuaTable buildArmor(Supplier<ClientPlayerEntity> playerSupplier) {
        LazyTable armor = new LazyTable();

        armor.addDouble("armor", () -> playerSupplier.get().getAttributeValue(EntityAttributes.GENERIC_ARMOR));
        // FIXME: toughness is always 0
        armor.addDouble("toughness", () -> playerSupplier.get().getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
        armor.addDouble("knockbackResistance", () -> playerSupplier.get().getAttributeValue(EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE));

        return armor.build();
    }

    private static LuaTable buildXp(Supplier<ClientPlayerEntity> playerSupplier) {
        LazyTable xp = new LazyTable();

        xp.addInt("total", () -> playerSupplier.get().totalExperience);
        xp.addInt("level", () -> playerSupplier.get().experienceLevel);
        xp.addDouble("progress", () -> playerSupplier.get().experienceProgress);

        return xp.build();
    }
}
