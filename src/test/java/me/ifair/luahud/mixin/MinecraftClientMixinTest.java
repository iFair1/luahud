package me.ifair.luahud.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftClientMixinTest {
    @Test void gpuMeasurementHookHasOneUnambiguousTargetInMinecraftRender() throws Exception {
        // Inspect the actual mapped Minecraft bytecode without initializing the client or OpenGL.
        // A Minecraft update adding another debug-HUD check must not silently broaden our hook.
        ClassNode client = new ClassNode(Opcodes.ASM9);
        try (InputStream source = getClass().getResourceAsStream("/net/minecraft/client/MinecraftClient.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(client, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode render = client.methods.stream()
                .filter(method -> method.name.equals("render") && method.desc.equals("(Z)V"))
                .findFirst().orElseThrow();

        int debugHudChecks = 0;
        boolean usesVanillaGpuTimer = false;
        for (AbstractInsnNode instruction : render.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("net/minecraft/client/gui/hud/DebugHud")
                    && call.name.equals("shouldShowDebugHud") && call.desc.equals("()Z")) {
                debugHudChecks++;
            }
            if (call.owner.equals("net/minecraft/client/gl/GlTimer") && call.name.equals("getInstance")) {
                usesVanillaGpuTimer = true;
            }
        }
        assertEquals(1, debugHudChecks, "The GPU measurement hook must match exactly one call");
        assertTrue(usesVanillaGpuTimer, "Minecraft must still own the GPU timer lifecycle");
    }
}
