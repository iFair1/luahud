package dev.ifair.luahud.render;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPainterTest {
    @Test
    void dragonHitboxPartsRenderTheirOwner() throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        // Only identity and ownership matter here; a real dragon constructor
        // requires a running world and its AI infrastructure.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        EnderDragonEntity dragon = (EnderDragonEntity) unsafe.allocateInstance(EnderDragonEntity.class);
        EnderDragonPart part = (EnderDragonPart) unsafe.allocateInstance(EnderDragonPart.class);
        Field owner = EnderDragonPart.class.getDeclaredField("owner");
        owner.setAccessible(true);
        owner.set(part, dragon);

        assertSame(dragon, EntityPainter.renderEntity(part));
        assertSame(dragon, EntityPainter.renderEntity(dragon));
    }

    @Test
    void ordinaryEntitiesAndNullKeepTheirRenderTarget() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        EndCrystalEntity crystal = new EndCrystalEntity(EntityType.END_CRYSTAL, null);

        assertSame(crystal, EntityPainter.renderEntity(crystal));
        assertNull(EntityPainter.renderEntity(null));
    }

    @Test
    void screenViewportPreservesOriginalHitboxTransformAtEveryScaleAndRotation() {
        for (int size : new int[] {12, 24, 60, 120}) {
            for (float height : new float[] {0.7f, 1.8f, 4f}) {
                for (int pitch : new int[] {0, 30, 90}) {
                    int x = 37;
                    int y = 53;
                    float scale = size / height;
                    Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI)
                            .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.PI / 4);
                    Matrix4f original = new Matrix4f()
                            .translate(x + size / 2f, y + size / 2f, 0)
                            .scale(scale, scale, -scale)
                            .translate(0, height / 2f, 0)
                            .rotate(rotation);
                    Vector3f translation = EntityPainter.viewportTranslation(
                            x, y, size, height, scale, 853, 481);
                    Matrix4f expanded = new Matrix4f()
                            .translate(853 / 2f, 481 / 2f, 0)
                            .scale(scale, scale, -scale)
                            .translate(translation)
                            .rotate(rotation);

                    assertTrue(original.equals(expanded, 0.0001f),
                            "Expanding the render target must not move or resize the entity");
                }
            }
        }
    }

    @Test
    void chickenHeadCanOverflowIconWithoutChangingScaleOrPosition() {
        int x = 100;
        int y = 100;
        int size = 60;
        float height = 0.7f;
        float scale = size / height;
        Vector3f translation = EntityPainter.viewportTranslation(x, y, size, height, scale, 800, 600);
        Vector3f head = new Matrix4f()
                .translate(400, 300, 0)
                .scale(scale, scale, -scale)
                .translate(translation)
                .rotateZ((float) Math.PI)
                .transformPosition(new Vector3f(0, 0.9375f, 0));

        assertEquals(x + size / 2f, head.x, 0.0001);
        assertEquals(y + size / 2f + scale * (height / 2f - 0.9375f), head.y, 0.0001);
        assertTrue(head.y < y, "The head should overflow the icon's top edge");
        assertTrue(head.y > 0, "The overflowing head remains inside the screen render target");
    }
}
