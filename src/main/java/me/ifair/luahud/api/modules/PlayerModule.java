package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.ModelBuilder;
import me.ifair.luahud.api.models.CoreModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerModule {
    private PlayerModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        ModelBuilder<ItemStack> stack = models.itemStack();
        api.root().namespace("player", player -> {
            player.string("id", "Namespaced player entity type", () -> Registries.ENTITY_TYPE.getId(client.player.getType()).toString())
                    .string("name", "Player display name", () -> client.player.getDisplayName().getString())
                    .string("uuid", "Player UUID", () -> client.player.getUuidAsString())
                    .number("x", "X coordinate", () -> client.player.getX())
                    .number("y", "Y coordinate", () -> client.player.getY())
                    .number("z", "Z coordinate", () -> client.player.getZ())
                    .integer("health", "Current health", () -> (int) client.player.getHealth())
                    .integer("maxHealth", "Maximum health", () -> (int) client.player.getMaxHealth())
                    .string("username", "Account username", () -> client.player.getGameProfile().getName())
                    .bool("sprinting", "Whether the character is sprinting", () -> client.player.isSprinting())
                    .bool("sprintHeld", "Whether the sprint key is held", () -> client.options.sprintKey.isPressed())
                    .string("gamemode", "Current game mode", () -> gameMode(client))
                    .value("effects", LuaType.map(models.statusEffect().type()), LuaDoc.of("Active status effects keyed by namespaced ID"),
                            () -> CoreModels.statusEffects(client.player));
            player.namespace("hunger", hunger -> hunger
                    .number("food", "Food level", () -> (double) client.player.getHungerManager().getFoodLevel())
                    .number("saturation", "Saturation level", () -> (double) client.player.getHungerManager().getSaturationLevel()));
            player.namespace("armor", armor -> armor
                    .number("armor", "Armor attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR))
                    .number("toughness", "Armor toughness attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR_TOUGHNESS))
                    .number("knockbackResistance", "Explosion knockback resistance", () -> attribute(client, EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE)));
            player.namespace("xp", xp -> xp
                    .integer("total", "Total experience points", () -> client.player.totalExperience)
                    .integer("level", "Experience level", () -> client.player.experienceLevel)
                    .number("progress", "Progress toward the next level", () -> (double) client.player.experienceProgress));
        });
    }

    private static double attribute(MinecraftClient client, RegistryEntry<EntityAttribute> attribute) {
        return client.player.getAttributeValue(attribute);
    }

    private static String gameMode(MinecraftClient client) {
        PlayerListEntry entry = client.player.networkHandler.getPlayerListEntry(client.player.getUuid());
        GameMode mode = entry == null ? null : entry.getGameMode();
        return mode == null ? "" : mode.name().toLowerCase(Locale.ROOT);
    }
}
