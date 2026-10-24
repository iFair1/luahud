package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.core.CobaltRuntime;
import dev.ifair.luahud.core.DeathEvent;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathModelTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void deathCallbackExposesPositionDimensionMessageAndWeapon() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.on('death', function(event)
                    x, y, z = event.x, event.y, event.z
                    dimension = event.dimension
                    message = event.message
                    damageType = event.damageType
                    noAttacker = event.attacker == nil
                    weaponId = event.weapon.id
                end)
                """, "death");
        runtime.fireEvent("death", new DeathEvent(-12.5, 64, 7.25, "minecraft:the_nether",
                "Player was slain", "minecraft:player_attack", null, new ItemStack(Items.IRON_SWORD)));
        assertEquals(-12.5, runtime.global("x").checkDouble());
        assertEquals(64.0, runtime.global("y").checkDouble());
        assertEquals(7.25, runtime.global("z").checkDouble());
        assertEquals("minecraft:the_nether", runtime.global("dimension").checkString());
        assertEquals("Player was slain", runtime.global("message").checkString());
        assertEquals("minecraft:player_attack", runtime.global("damageType").checkString());
        assertTrue(runtime.global("noAttacker").checkBoolean());
        assertEquals("minecraft:iron_sword", runtime.global("weaponId").checkString());
    }

    @Test void unavailableAttackerAndWeaponStayNil() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.on('death', function(event)
                    noAttacker = event.attacker == nil
                    noWeapon = event.weapon == nil
                    damageType = event.damageType
                end)
                """, "death");
        runtime.fireEvent("death", new DeathEvent(0, -64, 0, "minecraft:overworld",
                "Player died", "minecraft:generic", null, null));
        assertTrue(runtime.global("noAttacker").checkBoolean());
        assertTrue(runtime.global("noWeapon").checkBoolean());
        assertEquals("minecraft:generic", runtime.global("damageType").checkString());
    }

    private static CobaltRuntime runtime() throws org.squiddev.cobalt.LuaError {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.event("death", DeathModel.register(api, CoreModels.register(api)).type(), "Local player death");
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        return runtime;
    }
}
