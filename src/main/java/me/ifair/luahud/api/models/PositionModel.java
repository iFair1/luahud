package me.ifair.luahud.api.models;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class PositionModel extends LazyTable {
    public PositionModel(Supplier<Vec3d> posSupplier) {
        addDouble("x", () -> {
            Vec3d pos = posSupplier.get();
            return pos != null ? pos.x : 0.0;
        });
        addDouble("y", () -> {
            Vec3d pos = posSupplier.get();
            return pos != null ? pos.y : 0.0;
        });
        addDouble("z", () -> {
            Vec3d pos = posSupplier.get();
            return pos != null ? pos.z : 0.0;
        });
    }
}
