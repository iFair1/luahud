package dev.ifair.luahud.api.core;

import dev.ifair.luahud.core.CobaltRuntime;
import dev.ifair.luahud.core.DamageEvent;
import dev.ifair.luahud.core.DamageTracker;
import dev.ifair.luahud.core.DeathEvent;
import dev.ifair.luahud.core.DeathState;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CombatEventsTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(strings = {"damage", "attack"})
    void damageAndAttackExposeTheVictimAndAttackerThroughTheSameModel(String event) throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.on('" + event + "', function(damage) "
                + "victim = damage.victim.uuid; attacker = damage.attacker.uuid; "
                + "amount = damage.amount; weapon = damage.weapon.id end)", event);
        var victim = entity();
        var attacker = entity();
        DamageTracker tracker = new DamageTracker();
        tracker.bind(victim);
        tracker.onHealthUpdate(20);
        tracker.onDamage(new DamageEvent("minecraft:player_attack", attacker,
                new ItemStack(Items.IRON_SWORD), 0, victim));
        tracker.onHealthUpdate(15);
        assertNull(tracker.tick());
        DamageEvent damage = tracker.tick();
        assertSame(victim, damage.victim());
        assertSame(attacker, damage.attacker());
        runtime.fireEvent(event, damage);

        assertEquals(victim.getUuidAsString(), runtime.global("victim").checkString());
        assertEquals(attacker.getUuidAsString(), runtime.global("attacker").checkString());
        assertEquals(5, runtime.global("amount").checkDouble());
        assertEquals("minecraft:iron_sword", runtime.global("weapon").checkString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"death", "kill"})
    void deathAndKillExposeTheVictimThroughTheSameModel(String event) throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.on('" + event + "', function(death) "
                + "victim = death.victim.uuid; attacker = death.attacker.uuid; "
                + "x = death.x; message = death.message end)", event);
        var victim = entity();
        var attacker = entity();
        runtime.fireEvent(event, new DeathEvent(12, 64, -3, "minecraft:overworld", "Victim was slain",
                "minecraft:player_attack", attacker, null, victim));

        assertEquals(victim.getUuidAsString(), runtime.global("victim").checkString());
        assertEquals(attacker.getUuidAsString(), runtime.global("attacker").checkString());
        assertEquals(12, runtime.global("x").checkDouble());
        assertEquals("Victim was slain", runtime.global("message").checkString());
    }

    @Test void combatEventsReuseTheExistingModelDefinitions() {
        ApiRegistry registry = registry();
        assertSame(registry.event("damage").payloadType().model(), registry.event("attack").payloadType().model());
        assertSame(registry.event("death").payloadType().model(), registry.event("kill").payloadType().model());
        String stub = StubGenerator.generate(registry);
        assertFalse(stub.contains("---@class Attack\n"));
        assertFalse(stub.contains("---@class Kill\n"));
    }

    @Test void networkHandlerAppliesBothCombatMixins() throws Exception {
        Class<?> handler = Class.forName("net.minecraft.client.network.ClientPlayNetworkHandler", false,
                getClass().getClassLoader());
        assertTrue(DeathState.class.isAssignableFrom(handler));
        assertTrue(java.util.Arrays.stream(handler.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("luahud$observeKill")));
    }

    private static EndCrystalEntity entity() {
        return new EndCrystalEntity(EntityType.END_CRYSTAL, null);
    }

    private static ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(message -> { throw new AssertionError(message); });
        CoreApi.register(registry, null);
        registry.freeze();
        return registry;
    }

    private static CobaltRuntime runtime() throws org.squiddev.cobalt.LuaError {
        CobaltRuntime runtime = new CobaltRuntime(registry());
        runtime.start();
        return runtime;
    }
}
