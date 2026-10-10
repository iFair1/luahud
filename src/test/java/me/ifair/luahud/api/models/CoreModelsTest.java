package me.ifair.luahud.api.models;

import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.core.StubGenerator;
import me.ifair.luahud.api.modules.PlayerModule;
import me.ifair.luahud.core.CobaltRuntime;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.mob.ZombieHorseEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CoreModelsTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void statusEffectUsesDeclaredDurationNamesAndPreservesInfinity() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        AtomicReference<StatusEffectInstance> result = new AtomicReference<>();
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("effect", models.statusEffect().inputType(), "");
        capture.call(args -> { result.set(args.get(argument)); return null; });
        api.root().value("effect", models.statusEffect().type(), LuaDoc.NONE,
                () -> new StatusEffectInstance(StatusEffects.SPEED, -1, 2));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        runtime.execute("lh.capture({id='minecraft:speed', durationInSeconds=3})", "seconds");
        assertEquals(60, result.get().getDuration());
        assertEquals(0, result.get().getAmplifier());
        runtime.execute("lh.capture({id='minecraft:speed', durationInTicks=31, durationInSeconds=3, level=2})", "ticks");
        assertEquals(31, result.get().getDuration());
        assertEquals(1, result.get().getAmplifier());
        runtime.execute("seconds=lh.effect.durationInSeconds; lh.capture({id='minecraft:speed', durationInSeconds=-1})", "infinite");
        assertEquals(-1, runtime.global("seconds").checkInteger());
        assertEquals(-1, result.get().getDuration());
        runtime.execute("lh.capture({id='minecraft:speed', name='Speed', extra=true})", "wide-effect");
        assertEquals(0, result.get().getDuration());
    }

    @Test void explicitSecondsOverrideLazySourceTicks() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        AtomicReference<StatusEffectInstance> result = new AtomicReference<>();
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("effect", models.statusEffect().inputType(), "");
        capture.call(args -> { result.set(args.get(argument)); return null; });
        api.root().value("effect", models.statusEffect().type(), LuaDoc.NONE,
                () -> new StatusEffectInstance(StatusEffects.SPEED, 200));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        runtime.execute("effect=lh.effect; effect.durationInSeconds=3; lh.capture(effect)", "override");
        assertEquals(60, result.get().getDuration());
    }

    @Test void itemTablesNeedOnlyIdAndOverridesPreserveNativeComponents() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        AtomicReference<ItemStack> result = new AtomicReference<>();
        var capture = api.root().function("capture", LuaType.nil(), "");
        var argument = capture.parameter("stack", models.itemStack().inputType(), "");
        capture.call(args -> { result.set(args.get(argument)); return null; });
        ItemStack source = new ItemStack(Items.DIAMOND_PICKAXE);
        source.setDamage(100);
        source.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Custom pickaxe"));
        api.root().value("stack", models.itemStack().type(), LuaDoc.NONE, () -> source);
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();

        runtime.execute("lh.capture({id='minecraft:stone'})", "minimal-stack");
        assertEquals(Items.STONE, result.get().getItem());
        assertEquals(1, result.get().getCount());
        runtime.execute("stack=lh.stack; stack.damageLeft=10; lh.capture(stack)", "remaining-durability");
        assertEquals(source.getMaxDamage() - 10, result.get().getDamage());
        assertEquals(source.get(DataComponentTypes.CUSTOM_NAME), result.get().get(DataComponentTypes.CUSTOM_NAME));
        assertEquals(100, source.getDamage());
        runtime.execute("lh.capture(lh.stack)", "unchanged");
        assertSame(source, result.get());
    }

    @Test void horseFamilyUsesClientTrackedAttributesAndIncludesDonkeysAndMules() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        CoreModels models = CoreModels.register(registry.registrar("luahud"));
        for (Class<?> species : new Class<?>[] {HorseEntity.class, DonkeyEntity.class, MuleEntity.class,
                LlamaEntity.class, CamelEntity.class, SkeletonHorseEntity.class, ZombieHorseEntity.class}) {
            assertTrue(models.horse().javaType().isAssignableFrom(species), species.getSimpleName());
        }
        //? if <1.21.2 {
        assertTrue(EntityAttributes.GENERIC_MOVEMENT_SPEED.value().isTracked());
        assertTrue(EntityAttributes.GENERIC_JUMP_STRENGTH.value().isTracked());
        //?} else {
        /*assertTrue(EntityAttributes.MOVEMENT_SPEED.value().isTracked());
        assertTrue(EntityAttributes.JUMP_STRENGTH.value().isTracked());
        *///?}
        assertNull(CoreModels.horse(null));
    }

    @Test void horseAndRiderCyclesHaveCompleteStubsAndSeparateInputFields() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        CoreModels models = CoreModels.register(api);
        PlayerModule.register(api, null, models);
        var capture = api.root().function("capture", LuaType.nil(), "");
        capture.parameter("entity", models.entity().inputType(), "");
        capture.call(args -> null);
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        assertTrue(stub.contains("---@field horse? Horse"));
        assertTrue(stub.contains("---@field horse Horse? Currently ridden horse-family animal, or nil"));
        assertTrue(stub.contains("---@field rider? Entity"));
        for (String name : new String[] {"movementSpeed", "jumpStrength"}) {
            assertTrue(stub.contains("---@field " + name + " number"));
        }
        for (String name : new String[] {"isSaddled", "isTamed", "hasChest"}) {
            assertTrue(stub.contains("---@field " + name + " boolean"));
        }
        String input = stub.substring(stub.indexOf("---@class EntityInput"));
        input = input.substring(0, input.indexOf("\n\n"));
        assertTrue(input.contains("---@field uuid string"));
        assertFalse(input.contains("health"));
        assertEquals(stub, StubGenerator.generate(registry));
    }
}
