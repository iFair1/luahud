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
                    positioned.rowY() + RenderDimensions.ROW_HEIGHT
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
                int textY = y + (RenderDimensions.ROW_HEIGHT - textRenderer.fontHeight) / 2;
                context.drawText(textRenderer, text.value(), x, textY, text.textColor(), text.shadow());
            }
            case RenderElement.Stack stack -> paintItem(
                    context, textRenderer, stack.value(), stack.scale(), positioned.anchor(), x, y);
            case RenderElement.Item item -> paintItem(
                    context, textRenderer, item.value(), item.scale(), positioned.anchor(), x, y);
            case RenderElement.Texture texture -> {
                int size = RenderElementMeasurer.scaledIconSize(texture.scale());
                int textureY = anchoredIconY(positioned.anchor(), y, size);
                context.drawTexture(texture.value(), x, textureY, 0, 0, size, size, size, size);
            }
        }
    }

    private static void paintItem(
            DrawContext context,
            TextRenderer textRenderer,
            ItemStack stack,
            float requestedScale,
            Anchor anchor,
            int x,
            int rowY
    ) {
        if (stack == null || stack.isEmpty()) return;

        float scale = RenderElementMeasurer.normalizedScale(requestedScale);
        int size = RenderElementMeasurer.scaledIconSize(scale);
        float matrixScale = (RenderDimensions.BASE_ICON_SIZE / 16f) * scale;
        int itemY = anchoredIconY(anchor, rowY, size);

        context.getMatrices().push();
        context.getMatrices().translate(x, itemY, 0);
        context.getMatrices().scale(matrixScale, matrixScale, 1f);
        context.drawItem(stack, 0, 0);
        context.drawItemInSlot(textRenderer, stack, 0, 0);
        context.getMatrices().pop();
    }

    static int anchoredIconY(Anchor anchor, int rowY, int size) {
        return anchor.bottom() ? rowY + RenderDimensions.ROW_HEIGHT - size - 1 : rowY - 1;
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if ((color >>> 24) > 0) context.fill(x1, y1 - 1, x2, y2 - 1, color);
    }
}
