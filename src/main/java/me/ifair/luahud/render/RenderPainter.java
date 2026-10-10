package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import me.ifair.luahud.compat.GuiCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

final class RenderPainter {
    private RenderPainter() {}

    static void paint(DrawContext context, TextRenderer textRenderer, RenderLayout layout) {
        for (RenderLayout.PositionedElement positioned : layout.elements()) {
            RenderElement element = positioned.element();
            drawBackground(
                    context,
                    element.backgroundColor(),
                    positioned.backgroundX(),
                    positioned.backgroundY(),
                    positioned.backgroundX() + positioned.backgroundWidth(),
                    positioned.backgroundY() + positioned.rowHeight()
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
            case RenderElement.Entity entity -> EntityPainter.paint(
                    context, entity.value(), entity.scale(), entity.yaw(), entity.pitch(),
                    entity.fullBright(), entity.useEntityRotation(), positioned.sectionScale(),
                    x, y);
            case RenderElement.Texture texture -> {
                int size = RenderElementMeasurer.scaledIconSize(texture.scale(), positioned.sectionScale());
                GuiCompat.drawTexture(context, texture.value(), x, y - 1, 0, 0, size, size, size, size);
            }
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

        GuiCompat.push(context, x, y, scale);
        context.drawText(textRenderer, text.value(), 0, 0, text.textColor(), text.shadow());
        GuiCompat.pop(context);
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
        GuiCompat.push(context, x, y, matrixScale);
        context.drawItem(stack, 0, 0);
        //? if <1.21.2 {
        context.drawItemInSlot(textRenderer, stack, 0, 0);
        //?} else {
        /*context.drawStackOverlay(textRenderer, stack, 0, 0);
        *///?}
        GuiCompat.pop(context);
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if ((color >>> 24) > 0) context.fill(x1, y1, x2, y2, color);
    }
}
