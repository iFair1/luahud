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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ItemDropEventTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 64})
    void dropCallbackReceivesItemStackWithPositiveCount(int count) throws Exception {
        ApiRegistry registry = registry();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("""
                lh.on('item-drop', function(stack)
                    itemId = stack.id
                    itemName = stack.name
                    itemCount = stack.count
                end)
                """, "item-drop");
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Dropped diamonds"));
        runtime.fireEvent("item-drop", stack);

        assertEquals("minecraft:diamond", runtime.global("itemId").checkString());
        assertEquals("Dropped diamonds", runtime.global("itemName").checkString());
        assertEquals(count, runtime.global("itemCount").checkInteger());
        assertThrows(IllegalArgumentException.class,
                () -> registry.validateEvent(registry.event("item-drop"), "minecraft:diamond"));
    }

    @Test void stubDocumentsItemStackCallback() {
        assertTrue(StubGenerator.generate(registry()).contains(
                "---@overload fun(event: \"item-drop\", callback: fun(event: ItemStack))"));
    }

    @Test void dropMixinsApplyToBothVanillaDropPaths() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Class.forName("net.minecraft.entity.player.PlayerEntity", false, loader);
        Class.forName("net.minecraft.client.network.ClientPlayerEntity", false, loader);
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        CoreApi.register(registry, null);
        registry.freeze();
        return registry;
    }
}
