package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaDoc;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.models.RegistryIds;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.core.DeathState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.GameMode;

import java.util.Locale;
import java.util.function.Supplier;

public final class PlayerModule {
    private PlayerModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models,
                                ModelBuilder<DeathEvent> death) {
        register(api, client, models, death, () -> client.getNetworkHandler() == null ? null
                : ((DeathState) client.getNetworkHandler()).luahud$getLastDeath());
    }

    static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models,
                         ModelBuilder<DeathEvent> death, Supplier<DeathEvent> lastDeath) {
        api.root().namespace("player", player -> {
            player.value("id", RegistryIds.ENTITY_TYPE, LuaDoc.of("Namespaced player entity type"), () -> Registries.ENTITY_TYPE.getId(client.player.getType()).toString())
                    .string("name", "Player display name", () -> client.player.getDisplayName().getString())
                    .string("uuid", "Player UUID", () -> client.player.getUuidAsString())
                    .value("lastDeath", LuaType.optional(death.type()),
                            LuaDoc.of("Last observed death in the current connection, or nil before dying. Survives respawns, dimension changes and profile reloads; resets on reconnect."),
                            lastDeath)
                    .number("x", "X coordinate", () -> client.player.getX())
                    .number("y", "Y coordinate", () -> client.player.getY())
                    .number("z", "Z coordinate", () -> client.player.getZ())
                    .integer("health", "Current health", () -> (int) client.player.getHealth())
                    .integer("maxHealth", "Maximum health", () -> (int) client.player.getMaxHealth())
                    //? if <1.21.9 {
                    .string("username", "Account username", () -> client.player.getGameProfile().getName())
                    //?} else {
                    /*.string("username", "Account username", () -> client.player.getGameProfile().name())
                    *///?}
                    .bool("sprinting", "Whether the character is sprinting", () -> client.player.isSprinting())
                    .bool("sprintHeld", "Whether the sprint key is held", () -> client.options.sprintKey.isPressed())
                    .string("gamemode", "Current game mode", () -> gameMode(client))
                    .value("effects", LuaType.map(RegistryIds.STATUS_EFFECT, models.statusEffect().type()), LuaDoc.of("Active status effects keyed by namespaced ID"),
                            () -> CoreModels.statusEffects(client.player))
                    .value("horse", LuaType.optional(models.horse().type()), LuaDoc.of("Currently ridden horse-family animal, or nil"),
                            () -> CoreModels.horse(client.player.getVehicle()));
            player.namespace("hunger", hunger -> hunger
                    .number("food", "Food level", () -> (double) client.player.getHungerManager().getFoodLevel())
                    .number("saturation", "Saturation level", () -> (double) client.player.getHungerManager().getSaturationLevel()));
            player.namespace("armor", armor -> armor
                    //? if <1.21.2 {
                    .number("armor", "Armor attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR))
                    //?} else {
                    /*.number("armor", "Armor attribute", () -> attribute(client, EntityAttributes.ARMOR))
                    *///?}
                    //? if <1.21.2 {
                    .number("toughness", "Armor toughness attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR_TOUGHNESS))
                    //?} else {
                    /*.number("toughness", "Armor toughness attribute", () -> attribute(client, EntityAttributes.ARMOR_TOUGHNESS))
                    *///?}
                    //? if <1.21.2 {
                    .number("knockbackResistance", "Explosion knockback resistance", () -> attribute(client, EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE)));
                    //?} else {
                    /*.number("knockbackResistance", "Explosion knockback resistance", () -> attribute(client, EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)));
                    *///?}
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
