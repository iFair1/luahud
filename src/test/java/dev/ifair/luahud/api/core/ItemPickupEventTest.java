package dev.ifair.luahud.api.core;

import dev.ifair.luahud.core.CobaltRuntime;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemPickupEventTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void pickupCallbackReceivesTheExistingItemStackModel() throws Exception {
        ApiRegistry registry = registry();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("""
                lh.on('item-pickup', function(stack)
                    itemId = stack.id
                    itemName = stack.name
                    itemCount = stack.count
                end)
                """, "item-pickup");
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Picked-up diamonds"));
        runtime.fireEvent("item-pickup", stack);

        assertEquals("minecraft:diamond", runtime.global("itemId").checkString());
        assertEquals("Picked-up diamonds", runtime.global("itemName").checkString());
        assertEquals(3, runtime.global("itemCount").checkInteger());
        assertThrows(IllegalArgumentException.class,
                () -> registry.validateEvent(registry.event("item-pickup"), "minecraft:diamond"));
    }

    @Test void stubDocumentsItemStackCallback() {
        assertTrue(StubGenerator.generate(registry()).contains(
                "---@overload fun(event: \"item-pickup\", callback: fun(event: ItemStack))"));
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        CoreApi.register(registry, null);
        registry.freeze();
        return registry;
    }
}
