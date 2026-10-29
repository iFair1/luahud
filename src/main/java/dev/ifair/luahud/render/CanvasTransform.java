package dev.ifair.luahud.render;

/** Translation, uniform scale and clockwise rotation around an element-local pivot. */
public record CanvasTransform(float x, float y, float scale, float rotation, float originX, float originY) {
    public static final CanvasTransform IDENTITY = new CanvasTransform(0, 0, 1, 0, 0, 0);
    public CanvasTransform {
        for (float value : new float[]{x, y, scale, rotation, originX, originY}) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Canvas transform must be finite");
        }
        if (scale <= 0) throw new IllegalArgumentException("Scale must be greater than zero");
    }
}
