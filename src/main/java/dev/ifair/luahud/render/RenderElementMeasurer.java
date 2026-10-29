package dev.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;

final class RenderElementMeasurer {
    private RenderElementMeasurer() {}

    static int width(RenderElement element, TextRenderer textRenderer, float sectionScale) {
        return switch (element) {
            case RenderElement.Canvas canvas -> (int) Math.ceil(canvas.drawing().width * canvas.drawing().scale * sectionScale);
            case RenderElement.Text text -> RenderDimensions.scaleLength(
                    textRenderer.getWidth(text.value()), sectionScale);
            case RenderElement.Stack stack -> stackWidth(
                    stack.value(), stack.scale(), sectionScale, stack.drawAir());
            case RenderElement.Item item -> stackWidth(item.value(), item.scale(), sectionScale, false);
            case RenderElement.Entity entity -> scaledIconSize(entity.scale(), sectionScale);
            case RenderElement.Texture texture -> scaledIconSize(texture.scale(), sectionScale);
        };
    }

    static int height(RenderElement element, TextRenderer textRenderer, float sectionScale) {
        return switch (element) {
            case RenderElement.Canvas canvas -> (int) Math.ceil(canvas.drawing().height * canvas.drawing().scale * sectionScale);
            case RenderElement.Text ignored -> RenderDimensions.scaleLength(
                    textRenderer.fontHeight, sectionScale);
            case RenderElement.Stack stack -> stackHeight(
                    stack.value(), stack.scale(), sectionScale, stack.drawAir());
            case RenderElement.Item item -> stackHeight(
                    item.value(), item.scale(), sectionScale, false);
            case RenderElement.Entity entity -> scaledIconSize(entity.scale(), sectionScale);
            case RenderElement.Texture texture -> scaledIconSize(texture.scale(), sectionScale);
        };
    }

    static int stackWidth(ItemStack stack, float iconScale, float sectionScale, boolean drawAir) {
        return stackWidth(stack == null || stack.isEmpty(), iconScale, sectionScale, drawAir);
    }

    static int stackWidth(boolean empty, float iconScale, float sectionScale, boolean drawAir) {
        if (empty && !drawAir) return 0;
        return scaledIconSize(iconScale, sectionScale);
    }

    static int stackHeight(ItemStack stack, float iconScale, float sectionScale, boolean drawAir) {
        if ((stack == null || stack.isEmpty()) && !drawAir) return 0;
        return scaledIconSize(iconScale, sectionScale);
    }

    static int scaledIconSize(float iconScale, float sectionScale) {
        return RenderDimensions.scaleLength(
                RenderDimensions.BASE_ICON_SIZE, effectiveIconScale(iconScale, sectionScale));
    }

    static float effectiveIconScale(float iconScale, float sectionScale) {
        if (!Float.isFinite(iconScale) || iconScale <= 0
                || !Float.isFinite(sectionScale) || sectionScale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        float effective = iconScale * sectionScale;
        if (!Float.isFinite(effective)) throw new IllegalArgumentException("Combined icon scale is too large");
        return effective;
    }
}
