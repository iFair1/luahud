package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.models.DeathModel;
import dev.ifair.luahud.core.CobaltRuntime;
import dev.ifair.luahud.core.DeathEvent;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class PlayerModuleTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void lastDeathUsesTheEventModelAndReadsCurrentConnectionStateAcrossProfileReloads() throws Exception {
        AtomicReference<DeathEvent> lastDeath = new AtomicReference<>();
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        var death = DeathModel.register(api, models);
        PlayerModule.register(api, null, models, death, lastDeath::get);
        api.event("death", death.type(), "Local player death");
        registry.addEventFunctions();
        registry.freeze();

        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("assert(lh.player.lastDeath == nil)", "before-death");
        runtime.execute("""
                lh.on('death', function(event)
                    local last = lh.player.lastDeath
                    assert(last.x == event.x and last.y == event.y and last.z == event.z)
                    assert(last.dimension == event.dimension)
                    assert(last.message == event.message and last.damageType == event.damageType)
                    assert(last.attacker == nil)
                    assert(last.weapon.id == 'minecraft:iron_sword')
                    receivedDeath = true
                end)
                """, "death-handler");

        DeathEvent first = new DeathEvent(-12.5, 64, 7.25, "minecraft:the_nether",
                "Player was slain", "minecraft:player_attack", null, new ItemStack(Items.IRON_SWORD));
        lastDeath.set(first);
        runtime.fireEvent("death", first);
        runtime.execute("assert(receivedDeath)", "after-death");

        CobaltRuntime reloaded = new CobaltRuntime(registry);
        reloaded.start();
        reloaded.execute("assert(lh.player.lastDeath.x == -12.5); "
                + "assert(lh.player.lastDeath.dimension == 'minecraft:the_nether')", "reloaded-profile");

        lastDeath.set(new DeathEvent(100, -64, 200, "minecraft:overworld",
                "Player fell", "minecraft:fall", null, null));
        reloaded.execute("""
                local last = lh.player.lastDeath
                assert(last.x == 100 and last.y == -64 and last.z == 200)
                assert(last.dimension == 'minecraft:overworld' and last.damageType == 'minecraft:fall')
                assert(last.attacker == nil and last.weapon == nil)
                """, "next-death");

        lastDeath.set(null);
        reloaded.execute("assert(lh.player.lastDeath == nil)", "new-connection");
    }
}
