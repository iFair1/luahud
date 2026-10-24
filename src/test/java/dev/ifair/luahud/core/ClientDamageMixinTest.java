package dev.ifair.luahud.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClientDamageMixinTest {
    @Test void damageHookRunsOnceAfterVanillaSwitchesToTheClientThread() throws Exception {
        assertClientThreadTarget("onEntityDamage", "net/minecraft/entity/Entity", "onDamaged",
                "(Lnet/minecraft/entity/damage/DamageSource;)V");
    }

    @Test void healthHookRunsOnceAfterVanillaSwitchesToTheClientThread() throws Exception {
        assertClientThreadTarget("onHealthUpdate", "net/minecraft/client/network/ClientPlayerEntity", "updateHealth", "(F)V");
    }

    @Test void absorptionHookRunsOnceAfterVanillaSwitchesToTheClientThread() throws Exception {
        assertClientThreadTarget("onEntityTrackerUpdate", "net/minecraft/entity/data/DataTracker", "writeUpdatedEntries",
                "(Ljava/util/List;)V");
    }

    @Test void deathNotificationChecksTheClientThreadBeforeChoosingScreenOrImmediateRespawn() throws Exception {
        assertClientThreadTarget("onDeathMessage", "net/minecraft/client/network/ClientPlayerEntity", "showsDeathScreen", "()Z");
        assertClientThreadTarget("onDeathMessage", "net/minecraft/client/network/ClientPlayerEntity", "requestRespawn", "()V");
    }

    private void assertClientThreadTarget(String methodName, String owner, String name, String descriptor) throws Exception {
        ClassNode handler = new ClassNode(Opcodes.ASM9);
        try (InputStream source = getClass().getResourceAsStream("/net/minecraft/client/network/ClientPlayNetworkHandler.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(handler, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode method = handler.methods.stream()
                .filter(candidate -> candidate.name.equals(methodName))
                .findFirst().orElseThrow();
        int calls = 0;
        boolean threadCheck = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("forceMainThread")) threadCheck = true;
            if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) {
                assertTrue(threadCheck, "Damage callbacks must follow vanilla's client-thread check");
                calls++;
            }
        }
        assertEquals(1, calls, "The damage notification hook must have exactly one target");
    }
}
