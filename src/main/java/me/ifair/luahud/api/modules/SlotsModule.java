package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.ModelBuilder;
import me.ifair.luahud.api.models.CoreModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SlotsModule {
    private SlotsModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client, CoreModels models) {
        ModelBuilder<ItemStack> stack = models.itemStack();
        api.root().namespace("slots", slots -> slots
                .model("head", stack, "Head armor stack", () -> inventoryStack(client, 39))
                .model("chest", stack, "Chest armor stack", () -> inventoryStack(client, 38))
                .model("legs", stack, "Leg armor stack", () -> inventoryStack(client, 37))
                .model("feet", stack, "Feet armor stack", () -> inventoryStack(client, 36))
                .model("mainHand", stack, "Main-hand stack", () -> client.player.getInventory().getMainHandStack())
                .model("offHand", stack, "Off-hand stack", () -> client.player.getOffHandStack())
                .value("hotbar", LuaType.list(stack.type()), LuaDoc.of("Hotbar stacks at Lua indices 1-9"),
                        () -> inventoryStacks(client, 0, PlayerInventory.getHotbarSize()))
                .value("inventory", LuaType.list(stack.type()), LuaDoc.of("Main inventory stacks, excluding the hotbar, at Lua indices 1-27"),
                        () -> inventoryStacks(client, PlayerInventory.getHotbarSize(), PlayerInventory.MAIN_SIZE)));
    }

    private static ItemStack inventoryStack(MinecraftClient client, int slot) {
        return client.player.getInventory().getStack(slot);
    }

    private static List<ItemStack> inventoryStacks(MinecraftClient client, int fromIndex, int toIndex) {
        List<ItemStack> stacks = new ArrayList<>(toIndex - fromIndex);
        for (int slot = fromIndex; slot < toIndex; slot++) {
            stacks.add(client.player.getInventory().getStack(slot));
        }
        return stacks;
    }
}
