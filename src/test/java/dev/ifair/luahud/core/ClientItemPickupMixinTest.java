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

class ClientItemPickupMixinTest {
    @Test void pickupHookReadsStackOnceOnClientThreadBeforeVanillaConsumesIt() throws Exception {
        ClassNode handler = new ClassNode(Opcodes.ASM9);
        try (InputStream source = getClass().getResourceAsStream("/net/minecraft/client/network/ClientPlayNetworkHandler.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(handler, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode method = handler.methods.stream()
                .filter(candidate -> candidate.name.equals("onItemPickupAnimation"))
                .findFirst().orElseThrow();
        int stackReads = 0;
        boolean threadCheck = false;
        boolean decremented = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("forceMainThread")) threadCheck = true;
            if (call.owner.equals("net/minecraft/item/ItemStack") && call.name.equals("decrement")) {
                assertEquals(1, stackReads);
                decremented = true;
            }
            if (call.owner.equals("net/minecraft/entity/ItemEntity") && call.name.equals("getStack")
                    && call.desc.equals("()Lnet/minecraft/item/ItemStack;")) {
                assertTrue(threadCheck);
                assertFalse(decremented);
                stackReads++;
            }
        }
        assertEquals(1, stackReads);
        assertTrue(decremented);
    }
}
