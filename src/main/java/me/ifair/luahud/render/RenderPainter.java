package me.ifair.luahud.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;

final class RenderPainter {
    private RenderPainter() {}

    static void paint(DrawContext context, TextRenderer textRenderer, RenderLayout layout) {
        for (RenderLayout.PositionedElement positioned : layout.elements()) {
            RenderElement element = positioned.element();
            drawBackground(
                    context,
                    element.backgroundColor(),
                    positioned.backgroundX(),
                    positioned.rowY(),
                    positioned.backgroundX() + positioned.backgroundWidth(),
                    positioned.rowY() + positioned.rowHeight()
            );
            paintContent(context, textRenderer, positioned);
        }
    }

    private static void paintContent(
            DrawContext context,
            TextRenderer textRenderer,
            RenderLayout.PositionedElement positioned
    ) {
        RenderElement element = positioned.element();
        int x = positioned.contentX();
        int y = positioned.contentY();

        switch (element) {
            case RenderElement.Text text -> paintText(
                    context, textRenderer, text, positioned.sectionScale(), x, y);
            case RenderElement.Stack stack -> paintItem(
                    context, textRenderer, stack.value(), stack.scale(), positioned.sectionScale(),
                    x, y - 1);
            case RenderElement.Item item -> paintItem(
                    context, textRenderer, item.value(), item.scale(), positioned.sectionScale(),
                    x, y - 1);
            case RenderElement.Entity entity -> paintEntity(
                    context, entity.value(), entity.scale(), entity.yaw(), entity.pitch(),
                    entity.fullBright(), entity.useEntityRotation(), positioned.sectionScale(),
                    x, y - 1);
            case RenderElement.Texture texture -> {
                int size = RenderElementMeasurer.scaledIconSize(texture.scale(), positioned.sectionScale());
                context.drawTexture(texture.value(), x, y - 1, 0, 0, size, size, size, size);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void paintEntity(
            DrawContext context,
            Entity entity,
            float iconScale,
            float yaw,
            float pitch,
            boolean fullBright,
            boolean useEntityRotation,
            float sectionScale,
            int x,
            int y
    ) {
        if (entity == null || entity.isRemoved()) return;

        int size = RenderElementMeasurer.scaledIconSize(iconScale, sectionScale);
        float extent = Math.max(entity.getWidth(), entity.getHeight());
        if (!Float.isFinite(extent) || extent <= 0) return;
        float scale = size / extent;

        float originalYaw = entity.getYaw();
        float originalPitch = entity.getPitch();
        LivingRotation livingRotation = LivingRotation.capture(entity);
        MatrixStack matrices = context.getMatrices();
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf dispatcherRotation = new Quaternionf(dispatcher.getRotation());

        if (!useEntityRotation) {
            entity.setYaw(180f);
            entity.setPitch(0f);
            livingRotation.faceForward();
        }
        matrices.push();
        try {
            float yawRadians = (float) Math.toRadians(yaw);
            float pitchRadians = (float) Math.toRadians(pitch);
            Quaternionf verticalRotation = new Quaternionf().rotateX(pitchRadians);
            matrices.translate(x + size / 2f, y + size / 2f, 50);
            matrices.scale(scale, scale, -scale);
            matrices.translate(0, entity.getHeight() / 2f, 0);
            matrices.multiply(new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .mul(verticalRotation)
                    .rotateY(yawRadians));
            DiffuseLighting.method_34742();
            dispatcher.setRotation(new Quaternionf(verticalRotation).conjugate().rotateY((float) Math.PI));
            dispatcher.setRenderShadows(false);
            float entityYaw = useEntityRotation ? originalYaw : 0;
            int light = fullBright ? 0xF000F0 : dispatcher.getLight(entity, 1);
            RenderSystem.runAsFancy(() -> dispatcher.render(
                    entity, 0, 0, 0, entityYaw, 1, matrices, context.getVertexConsumers(), light));
            context.draw();
        } finally {
            dispatcher.setRenderShadows(true);
            dispatcher.setRotation(dispatcherRotation);
            matrices.pop();
            DiffuseLighting.enableGuiDepthLighting();
            livingRotation.restore();
            entity.setYaw(originalYaw);
            entity.setPitch(originalPitch);
        }
    }

    private record LivingRotation(
            LivingEntity entity,
            float bodyYaw,
            float headYaw,
            float prevHeadYaw
    ) {
        private static LivingRotation capture(Entity entity) {
            return entity instanceof LivingEntity living
                    ? new LivingRotation(living, living.bodyYaw, living.headYaw, living.prevHeadYaw)
                    : new LivingRotation(null, 0, 0, 0);
        }

        private void faceForward() {
            if (entity == null) return;
            entity.bodyYaw = 180f;
            entity.headYaw = 180f;
            entity.prevHeadYaw = 180f;
        }

        private void restore() {
            if (entity == null) return;
            entity.bodyYaw = bodyYaw;
            entity.headYaw = headYaw;
            entity.prevHeadYaw = prevHeadYaw;
        }
    }

    private static void paintText(
            DrawContext context,
            TextRenderer textRenderer,
            RenderElement.Text text,
            float scale,
            int x,
            int y
    ) {
        if (scale == 1f) {
            context.drawText(textRenderer, text.value(), x, y, text.textColor(), text.shadow());
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawText(textRenderer, text.value(), 0, 0, text.textColor(), text.shadow());
        context.getMatrices().pop();
    }

    private static void paintItem(
            DrawContext context,
            TextRenderer textRenderer,
            ItemStack stack,
            float iconScale,
            float sectionScale,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty()) return;

        float scale = RenderElementMeasurer.effectiveIconScale(iconScale, sectionScale);
        float matrixScale = (RenderDimensions.BASE_ICON_SIZE / 16f) * scale;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(matrixScale, matrixScale, 1f);
        context.drawItem(stack, 0, 0);
        context.drawItemInSlot(textRenderer, stack, 0, 0);
        context.getMatrices().pop();
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if ((color >>> 24) > 0) context.fill(x1, y1 - 1, x2, y2 - 1, color);
    }
}
