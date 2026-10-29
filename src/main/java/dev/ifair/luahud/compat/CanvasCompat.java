package dev.ifair.luahud.compat;

import dev.ifair.luahud.render.CanvasGeometry;
import dev.ifair.luahud.render.CanvasTransform;
import net.minecraft.client.gui.DrawContext;
import org.joml.Vector2f;
import java.util.List;

/** Version-specific matrix and geometry operations for locally positioned HUD drawings. */
public final class CanvasCompat {
    private CanvasCompat() {}

    public static void push(DrawContext context, CanvasTransform transform) {
        //? if <1.21.6 {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(transform.x() + transform.originX() * transform.scale(),
                transform.y() + transform.originY() * transform.scale(), 0);
        matrices.multiply(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(transform.rotation())));
        matrices.scale(transform.scale(), transform.scale(), 1);
        matrices.translate(-transform.originX(), -transform.originY(), 0);
        //?} else {
        /*var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(transform.x() + transform.originX() * transform.scale(),
                transform.y() + transform.originY() * transform.scale());
        matrices.rotate((float) Math.toRadians(transform.rotation()));
        matrices.scale(transform.scale(), transform.scale());
        matrices.translate(-transform.originX(), -transform.originY());
        *///?}
    }

    public static void scale(DrawContext context, float x, float y) {
        //? if <1.21.6 {
        context.getMatrices().scale(x, y, 1);
        //?} else {
        /*context.getMatrices().scale(x, y);
        *///?}
    }

    /** A separate layer per display-list command preserves painter order across GUI pipelines. */
    public static void nextLayer(DrawContext context) {
        //? if <1.21.6 {
        context.draw();
        //?} else {
        /*context.createNewRootLayer();
        *///?}
    }

    /** Legacy model rendering writes depth in front of later 2D commands. Keep its color,
     * but release that depth before resuming the display list. */
    public static void finishModel(DrawContext context) {
        //? if <1.21.6 {
        context.draw();
        //? if <1.21.2 {
        com.mojang.blaze3d.systems.RenderSystem.clear(256, net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC);
        //?} else if <1.21.5 {
        /*com.mojang.blaze3d.systems.RenderSystem.clear(256);
        *///?} else {
        /*var depth = net.minecraft.client.MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();
        if (depth != null) com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .clearDepthTexture(depth, 1);
        *///?}
        //?}
    }

    public static void mesh(DrawContext context, List<CanvasGeometry.Quad> quads) {
        if (quads.isEmpty()) return;
        //? if <1.21.2 {
        emit(context.getVertexConsumers().getBuffer(net.minecraft.client.render.RenderLayer.getGui()),
                context.getMatrices().peek().getPositionMatrix(), quads);
        context.draw();
        //?} else if <1.21.6 {
        /*context.draw(consumers -> emit(consumers.getBuffer(net.minecraft.client.render.RenderLayer.getGui()),
                context.getMatrices().peek().getPositionMatrix(), quads));
        *///?} else {
        /*var access = (dev.ifair.luahud.mixin.CanvasDrawContextAccessor) context;
        var pose = new org.joml.Matrix3x2f(context.getMatrices());
        var scissor = access.luahud$getScissorStack().peekLast();
        access.luahud$getState().addSimpleElement(new CanvasMeshState(quads, pose, scissor));
        *///?}
    }

    //? if <1.21.6 {
    private static void emit(net.minecraft.client.render.VertexConsumer consumer, org.joml.Matrix4f pose,
                             List<CanvasGeometry.Quad> quads) {
        for (var quad : quads) {
            for (var vertex : new CanvasGeometry.Vertex[]{quad.a(), quad.b(), quad.c(), quad.d()}) {
                consumer.vertex(pose, vertex.x(), vertex.y(), 0).color(vertex.color());
            }
        }
    }
    //?}

    //? if >=1.21.6 {
    /*private record CanvasMeshState(List<CanvasGeometry.Quad> quads, org.joml.Matrix3x2f pose,
                                   net.minecraft.client.gui.ScreenRect scissorArea,
                                   net.minecraft.client.gui.ScreenRect bounds)
            implements net.minecraft.client.gui.render.state.SimpleGuiElementRenderState {
        CanvasMeshState(List<CanvasGeometry.Quad> quads, org.joml.Matrix3x2f pose,
                        net.minecraft.client.gui.ScreenRect scissor) {
            this(List.copyOf(quads), pose, scissor, bounds(quads, pose, scissor));
        }

        private static net.minecraft.client.gui.ScreenRect bounds(List<CanvasGeometry.Quad> quads,
                org.joml.Matrix3x2f pose, net.minecraft.client.gui.ScreenRect scissor) {
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (var quad : quads) {
                for (var v : new CanvasGeometry.Vertex[]{quad.a(), quad.b(), quad.c(), quad.d()}) {
                    Vector2f p = pose.transformPosition(v.x(), v.y(), new Vector2f());
                    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
                    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
                }
            }
            var result = new net.minecraft.client.gui.ScreenRect((int) Math.floor(minX), (int) Math.floor(minY),
                    (int) Math.ceil(maxX) - (int) Math.floor(minX), (int) Math.ceil(maxY) - (int) Math.floor(minY));
            return scissor == null ? result : result.intersection(scissor);
        }

        @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return net.minecraft.client.gl.RenderPipelines.GUI;
        }
        @Override public net.minecraft.client.texture.TextureSetup textureSetup() {
            return net.minecraft.client.texture.TextureSetup.empty();
        }
        //? if <1.21.9 {
        @Override public void setupVertices(net.minecraft.client.render.VertexConsumer consumer, float depth) {
        //?} else {
        @Override public void setupVertices(net.minecraft.client.render.VertexConsumer consumer) {
            float depth = 0;
        //?}
            for (var quad : quads) {
                for (var v : new CanvasGeometry.Vertex[]{quad.a(), quad.b(), quad.c(), quad.d()}) {
                    Vector2f p = pose.transformPosition(v.x(), v.y(), new Vector2f());
                    consumer.vertex(p.x, p.y, depth).color(v.color());
                }
            }
        }
    }
    *///?}
}
