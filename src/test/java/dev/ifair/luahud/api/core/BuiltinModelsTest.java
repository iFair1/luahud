package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.BuiltinModels;
import dev.ifair.luahud.api.EventType;
import dev.ifair.luahud.api.LuaDoc;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.core.CobaltRuntime;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinModelsTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void addonsReceiveTheExistingFrozenModelsWithoutRegisteringCopies() {
        ApiRegistry registry = registry();
        var existing = registry.models();
        registry.registerAddon("myaddon-for-luahud", api -> {
            BuiltinModels models = api.models();
            for (LuaType<?> type : List.of(models.block(), models.entity(), models.itemStack(), models.statusEffect(),
                    models.horse(), models.damage(), models.death(), models.error())) {
                assertTrue(existing.stream().anyMatch(model -> model == type.model()));
                assertEquals("luahud", type.model().providerId());
                assertThrows(IllegalStateException.class, () -> type.model().fromFields(fields -> null));
                assertThrows(IllegalStateException.class, () -> type.model().fields().getFirst().inputRequired());
            }
            assertSame(registry.event("item-pickup").payloadType().model(), models.itemStack().model());
            assertSame(registry.event("damage").payloadType().model(), models.damage().model());
            assertSame(registry.event("death").payloadType().model(), models.death().model());
            assertSame(registry.event("error").payloadType().model(), models.error().model());
        });
        assertEquals(existing, registry.models());
        registry.freeze();
    }

    @Test void addonCanReuseItemStacksInValuesFunctionsFieldsAndEventsWithItsOwnNamespace() throws Exception {
        ApiRegistry registry = registry();
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        AtomicReference<EventType<ItemStack>> event = new AtomicReference<>();
        AtomicReference<ItemStack> captured = new AtomicReference<>();
        registry.registerAddon("myaddon-for-luahud", api -> {
            var itemStack = api.models().itemStack();
            var wrapper = api.model("Wrapper", ItemStack.class);
            wrapper.field("item", itemStack, "Wrapped item", value -> value);
            api.root().namespace("myaddon", ns -> {
                ns.value("item", itemStack, LuaDoc.NONE, () -> stack);
                ns.value("wrapped", wrapper.type(), LuaDoc.NONE, () -> stack);
                var echo = ns.function("echo", itemStack, "Echo an item");
                var input = echo.parameter("item", itemStack, "Item or input table");
                echo.call(args -> {
                    captured.set(args.get(input));
                    return captured.get();
                });
            });
            event.set(api.event("addon-item", itemStack, "Addon item"));
        });
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("""
                assert(lh.myaddon.item.id == 'minecraft:diamond')
                assert(lh.myaddon.wrapped.item.count == 3)
                assert(lh.myaddon.echo(lh.myaddon.item).count == 3)
                lh.on('addon-item', function(item) received = item.id end)
                """, "addon-models");
        assertSame(stack, captured.get());
        runtime.execute("assert(lh.myaddon.echo({id='minecraft:diamond', count=5}).count == 5)", "addon-input");
        assertEquals(5, captured.get().getCount());
        runtime.fireEvent(event.get(), stack);
        assertEquals("minecraft:diamond", runtime.global("received").checkString());
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@return ItemStack"));
        assertFalse(stub.contains("myaddon_for_luahud_ItemStack"));
    }

    @Test void missingCoreRegistrationHasAClearDiagnostic() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("addon");
        assertThrows(IllegalStateException.class, api::models);
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(message -> { throw new AssertionError(message); });
        CoreApi.register(registry, null);
        return registry;
    }
}
