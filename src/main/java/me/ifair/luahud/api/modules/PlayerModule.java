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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.GameMode;

import java.util.Locale;
import java.util.Map;

public final class PlayerModule {
    private PlayerModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        ModelBuilder<ItemStack> stack = models.itemStack();
        api.root().namespace("player", player -> {
            player.string("id", "Namespaced player entity type", () -> client.player == null ? "" : Registries.ENTITY_TYPE.getId(client.player.getType()).toString())
                    .string("name", "Player display name", () -> client.player == null ? "" : client.player.getDisplayName().getString())
                    .string("uuid", "Player UUID", () -> client.player == null ? "" : client.player.getUuidAsString())
                    .number("x", "X coordinate", () -> client.player == null ? 0.0 : client.player.getX())
                    .number("y", "Y coordinate", () -> client.player == null ? 0.0 : client.player.getY())
                    .number("z", "Z coordinate", () -> client.player == null ? 0.0 : client.player.getZ())
                    .number("health", "Current health", () -> client.player == null ? 0.0 : (double) client.player.getHealth())
                    .number("maxHealth", "Maximum health", () -> client.player == null ? 0.0 : (double) client.player.getMaxHealth())
                    .string("username", "Account username", () -> client.player == null ? "" : client.player.getGameProfile().getName())
                    .bool("sprinting", "Whether the character is sprinting", () -> client.player != null && client.player.isSprinting())
                    .bool("sprintHeld", "Whether the sprint key is held", () -> client.options.sprintKey.isPressed())
                    .string("gamemode", "Current game mode", () -> gameMode(client))
                    .value("effects", LuaType.map(models.statusEffect().type()), LuaDoc.of("Active status effects keyed by namespaced ID"),
                            () -> client.player == null ? Map.of() : CoreModels.statusEffects(client.player));
            player.namespace("hunger", hunger -> hunger
                    .number("food", "Food level", () -> client.player == null ? 0.0 : (double) client.player.getHungerManager().getFoodLevel())
                    .number("saturation", "Saturation level", () -> client.player == null ? 0.0 : (double) client.player.getHungerManager().getSaturationLevel()));
            player.namespace("armor", armor -> armor
                    .number("armor", "Armor attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR))
                    .number("toughness", "Armor toughness attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR_TOUGHNESS))
                    .number("knockbackResistance", "Explosion knockback resistance", () -> attribute(client, EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE)));
            player.namespace("xp", xp -> xp
                    .integer("total", "Total experience points", () -> client.player == null ? 0 : client.player.totalExperience)
                    .integer("level", "Experience level", () -> client.player == null ? 0 : client.player.experienceLevel)
                    .number("progress", "Progress toward the next level", () -> client.player == null ? 0.0 : (double) client.player.experienceProgress));
            player.namespace("slots", slots -> slots
                    .model("head", stack, "Head armor stack", () -> inventoryStack(client, 39))
                    .model("chest", stack, "Chest armor stack", () -> inventoryStack(client, 38))
                    .model("legs", stack, "Leg armor stack", () -> inventoryStack(client, 37))
                    .model("feet", stack, "Feet armor stack", () -> inventoryStack(client, 36))
                    .model("mainHand", stack, "Main-hand stack", () -> client.player == null ? ItemStack.EMPTY : client.player.getInventory().getMainHandStack())
                    .model("offHand", stack, "Off-hand stack", () -> client.player == null ? ItemStack.EMPTY : client.player.getOffHandStack()));
        });
    }

    private static double attribute(MinecraftClient client, RegistryEntry<EntityAttribute> attribute) {
        return client.player == null ? 0 : client.player.getAttributeValue(attribute);
    }

    private static ItemStack inventoryStack(MinecraftClient client, int slot) {
        return client.player == null ? ItemStack.EMPTY : client.player.getInventory().getStack(slot);
    }

    private static String gameMode(MinecraftClient client) {
        if (client.player == null) return "";
        PlayerListEntry entry = client.player.networkHandler.getPlayerListEntry(client.player.getUuid());
        GameMode mode = entry == null ? null : entry.getGameMode();
        return mode == null ? "" : mode.name().toLowerCase(Locale.ROOT);
    }
}
