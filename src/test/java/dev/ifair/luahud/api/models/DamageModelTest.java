package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.core.StubGenerator;
import dev.ifair.luahud.core.CobaltRuntime;
import dev.ifair.luahud.core.DamageEvent;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageModelTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void damageCallbackExposesWeaponAndMeasuredHealthLoss() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.on('damage', function(event)
                    damageType = event.type
                    environmental = event.attacker == nil
                    weaponId = event.weapon.id
                    weaponCount = event.weapon.count
                    amount = event.amount
                end)
                """, "damage");
        runtime.fireEvent("damage", new DamageEvent("minecraft:player_attack", null,
                new ItemStack(Items.IRON_SWORD), 3.0));
        assertEquals("minecraft:player_attack", runtime.global("damageType").checkString());
        assertTrue(runtime.global("environmental").checkBoolean());
        assertEquals("minecraft:iron_sword", runtime.global("weaponId").checkString());
        assertEquals(1, runtime.global("weaponCount").checkInteger());
        assertEquals(3.0, runtime.global("amount").checkDouble());
    }

    @Test void unavailableEntitiesStayNilAndZeroHealthLossStaysZero() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.on('damage', function(event)
                    noAttacker = event.attacker == nil
                    noWeapon = event.weapon == nil
                    amount = event.amount
                end)
                """, "damage");
        runtime.fireEvent("damage", new DamageEvent("minecraft:fall", null, null,
                0.0));
        assertTrue(runtime.global("noAttacker").checkBoolean());
        assertTrue(runtime.global("noWeapon").checkBoolean());
        assertEquals(0.0, runtime.global("amount").checkDouble());
    }

    @Test void stubDocumentsTypedDamageCallback() {
        ApiRegistry registry = registry();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@class Damage\n"));
        assertFalse(stub.contains("---@class DamageAmount\n"));
        assertTrue(stub.contains("---@field attacker? Entity"));
        assertTrue(stub.contains("---@field weapon? ItemStack"));
        assertTrue(stub.contains("---@field amount number"));
        assertTrue(stub.contains("including absorption in health points (two per heart)"));
        assertTrue(stub.contains("---@overload fun(event: \"damage\", callback: fun(event: Damage))"));
        assertThrows(IllegalArgumentException.class, () -> registry.event("entity_damage"));
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.event("damage", DamageModel.register(api, CoreModels.register(api)).type(), "Local player damage");
        registry.addEventFunctions();
        registry.freeze();
        return registry;
    }

    private static CobaltRuntime runtime() throws org.squiddev.cobalt.LuaError {
        CobaltRuntime runtime = new CobaltRuntime(registry());
        runtime.start();
        return runtime;
    }
}
