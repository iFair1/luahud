package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
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
        int y = positioned.rowY();

        switch (element) {
            case RenderElement.Text text -> {
                int textHeight = RenderDimensions.scaleLength(textRenderer.fontHeight, positioned.sectionScale());
                int textY = y + (positioned.rowHeight() - textHeight) / 2;
                paintText(context, textRenderer, text, positioned.sectionScale(), x, textY);
            }
            case RenderElement.Stack stack -> paintItem(
                    context, textRenderer, stack.value(), stack.scale(), positioned.sectionScale(),
                    positioned.anchor(), positioned.rowHeight(), x, y);
            case RenderElement.Item item -> paintItem(
                    context, textRenderer, item.value(), item.scale(), positioned.sectionScale(),
                    positioned.anchor(), positioned.rowHeight(), x, y);
            case RenderElement.Texture texture -> {
                int size = RenderElementMeasurer.scaledIconSize(texture.scale(), positioned.sectionScale());
                int textureY = anchoredIconY(positioned.anchor(), y, size, positioned.rowHeight());
                context.drawTexture(texture.value(), x, textureY, 0, 0, size, size, size, size);
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
            Anchor anchor,
            int rowHeight,
            int x,
            int rowY
    ) {
        if (stack == null || stack.isEmpty()) return;

        float scale = RenderElementMeasurer.effectiveIconScale(iconScale, sectionScale);
        int size = RenderElementMeasurer.scaledIconSize(iconScale, sectionScale);
        float matrixScale = (RenderDimensions.BASE_ICON_SIZE / 16f) * scale;
        int itemY = anchoredIconY(anchor, rowY, size, rowHeight);

        context.getMatrices().push();
        context.getMatrices().translate(x, itemY, 0);
        context.getMatrices().scale(matrixScale, matrixScale, 1f);
        context.drawItem(stack, 0, 0);
        context.drawItemInSlot(textRenderer, stack, 0, 0);
        context.getMatrices().pop();
    }

    static int anchoredIconY(Anchor anchor, int rowY, int size, int rowHeight) {
        return anchor.bottom() ? rowY + rowHeight - size - 1 : rowY - 1;
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if ((color >>> 24) > 0) context.fill(x1, y1 - 1, x2, y2 - 1, color);
    }
}
