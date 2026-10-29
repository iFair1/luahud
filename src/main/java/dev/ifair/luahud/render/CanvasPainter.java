package dev.ifair.luahud.render;

import dev.ifair.luahud.compat.CanvasCompat;
import dev.ifair.luahud.compat.GuiCompat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

final class CanvasPainter {
    private CanvasPainter() {}

    static void paint(DrawContext context, TextRenderer textRenderer, CanvasDrawing canvas,
                      int x, int y, float sectionScale) {
        if (!canvas.visible) return;
        float scale = sectionScale * canvas.scale;
        // The bounds are fixed before command-local transforms are applied.
        CanvasCompat.push(context, new CanvasTransform(x, y, scale, 0, 0, 0));
        boolean clipped = false;
        try {
            if (canvas.clip) {
                // Before 1.21.4 scissor coordinates are already in screen space.
                //? if <1.21.4 {
                context.enableScissor(x, y, x + (int) Math.ceil(canvas.width * scale),
                        y + (int) Math.ceil(canvas.height * scale));
                //?} else {
                /*context.enableScissor(0, 0, (int) Math.ceil(canvas.width), (int) Math.ceil(canvas.height));
                *///?}
                clipped = true;
            }
            for (var recorded : canvas.commands()) {
                var command = recorded instanceof CanvasDrawing.Faded faded ? faded.command() : recorded;
                float opacity = recorded instanceof CanvasDrawing.Faded faded ? faded.opacity()
                        : command instanceof CanvasDrawing.Content content ? content.element().opacity() : 1;
                if (opacity == 0) continue;
                Opacity.begin(context, opacity);
                try {
                    CanvasCompat.nextLayer(context);
                    switch (command) {
                        case CanvasDrawing.Faded ignored -> throw new IllegalStateException("Nested opacity command");
                        case CanvasDrawing.Mesh mesh -> CanvasCompat.mesh(context, mesh.quads());
                        case CanvasDrawing.Content content -> {
                            CanvasCompat.push(context, content.transform());
                            try {
                                content(context, textRenderer, content.element());
                                if (content.element() instanceof RenderElement.Entity
                                        || content.element() instanceof RenderElement.Item
                                        || content.element() instanceof RenderElement.Stack) {
                                    CanvasCompat.finishModel(context);
                                }
                            } finally { GuiCompat.pop(context); }
                        }
                        case CanvasDrawing.Image image -> {
                            CanvasCompat.push(context, image.transform());
                            try { image(context, image); }
                            finally { GuiCompat.pop(context); }
                        }
                    }
                } finally { Opacity.end(context); }
            }
            CanvasCompat.nextLayer(context);
        } finally {
            if (clipped) context.disableScissor();
            GuiCompat.pop(context);
        }
    }

    private static void content(DrawContext context, TextRenderer textRenderer, RenderElement element) {
        int width = RenderElementMeasurer.width(element, textRenderer, 1);
        int height = RenderElementMeasurer.height(element, textRenderer, 1);
        if ((element.backgroundColor() >>> 24) != 0) context.fill(0, 0, width, height, element.backgroundColor());
        // A background and its content may use different deferred pipelines.
        CanvasCompat.nextLayer(context);
        switch (element) {
            case RenderElement.Text text -> context.drawText(textRenderer, text.value(), 0, 0, text.textColor(), text.shadow());
            case RenderElement.Item item -> item(context, textRenderer, item.value());
            case RenderElement.Stack stack -> item(context, textRenderer, stack.value());
            case RenderElement.Entity entity -> EntityPainter.paintCanvas(context, entity);
            default -> throw new IllegalStateException("Unsupported canvas content");
        }
    }

    private static void item(DrawContext context, TextRenderer textRenderer, net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) return;
        CanvasCompat.push(context, new CanvasTransform(0, 0, RenderDimensions.BASE_ICON_SIZE / 16f, 0, 0, 0));
        try {
            context.drawItem(stack, 0, 0);
            //? if <1.21.2 {
            context.drawItemInSlot(textRenderer, stack, 0, 0);
            //?} else {
            /*context.drawStackOverlay(textRenderer, stack, 0, 0);
            *///?}
        } finally { GuiCompat.pop(context); }
    }

    private static void image(DrawContext context, CanvasDrawing.Image image) {
        if (!image.nineSlice()) {
            patch(context, image, 0, 0, image.width(), image.height(), image.sourceX(), image.sourceY(),
                    image.regionWidth(), image.regionHeight());
            return;
        }
        float shrinkX = Math.min(1, image.width() / Math.max(1, image.left() + image.right()));
        float shrinkY = Math.min(1, image.height() / Math.max(1, image.top() + image.bottom()));
        float[] dx = {0, image.left() * shrinkX, image.width() - image.right() * shrinkX, image.width()};
        float[] dy = {0, image.top() * shrinkY, image.height() - image.bottom() * shrinkY, image.height()};
        int[] sx = {0, image.left(), image.sourceWidth() - image.right(), image.sourceWidth()};
        int[] sy = {0, image.top(), image.sourceHeight() - image.bottom(), image.sourceHeight()};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                patch(context, image, dx[col], dy[row], dx[col + 1] - dx[col], dy[row + 1] - dy[row],
                        sx[col], sy[row], sx[col + 1] - sx[col], sy[row + 1] - sy[row]);
            }
        }
    }

    private static void patch(DrawContext context, CanvasDrawing.Image image, float x, float y,
                              float width, float height, int sx, int sy, int sw, int sh) {
        if (width <= 0 || height <= 0 || sw == 0 || sh == 0) return;
        CanvasCompat.push(context, new CanvasTransform(x, y, 1, 0, 0, 0));
        try {
            CanvasCompat.scale(context, width / sw, height / sh);
            GuiCompat.drawTexture(context, image.texture(), 0, 0, sx, sy, sw, sh, image.sourceWidth(), image.sourceHeight());
        } finally { GuiCompat.pop(context); }
    }
}
