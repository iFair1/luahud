package me.ifair.luahud.compat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCoordinatesTest {
    @Test void resolvesAbsoluteAndRelativeCoordinatesWithoutAServer() {
        Vec3d position = new Vec3d(10.5, 64, -10.5);
        assertEquals(new BlockPos(1, 2, -3), ClientCoordinates.resolve("1 2 -3", position, Vec2f.ZERO));
        assertEquals(new BlockPos(11, 66, -12), ClientCoordinates.resolve("~1 ~2 ~-1", position, Vec2f.ZERO));
    }

    @Test void localCoordinatesUsePlayerRotationAndFeetAnchor() {
        Vec3d position = new Vec3d(10.5, 64, -10.5);
        assertEquals(new BlockPos(10, 64, -9), ClientCoordinates.resolve("^ ^ ^2", position, Vec2f.ZERO));
        assertEquals(new BlockPos(8, 64, -11), ClientCoordinates.resolve("^ ^ ^2", position, new Vec2f(0, 90)));
        assertEquals(new BlockPos(10, 66, -11), ClientCoordinates.resolve("^ ^2 ^", position, Vec2f.ZERO));
    }

    @Test void rejectsMixedLocalAndWorldCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientCoordinates.resolve("^1 ~2 3", Vec3d.ZERO, Vec2f.ZERO));
    }
}
