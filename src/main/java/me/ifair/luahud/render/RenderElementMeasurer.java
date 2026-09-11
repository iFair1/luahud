package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;

final class RenderElementMeasurer {
    private RenderElementMeasurer() {}

    static int width(RenderElement element, TextRenderer textRenderer) {
        return switch (element) {
            case RenderElement.Text text -> textRenderer.getWidth(text.value());
            case RenderElement.Stack stack -> stackWidth(stack.value(), stack.scale(), stack.drawAir());
            case RenderElement.Item item -> stackWidth(item.value(), item.scale(), false);
            case RenderElement.Texture texture -> scaledIconSize(texture.scale());
        };
    }

    static int stackWidth(ItemStack stack, float scale, boolean drawAir) {
        return stackWidth(stack == null || stack.isEmpty(), scale, drawAir);
    }

    static int stackWidth(boolean empty, float scale, boolean drawAir) {
        if (empty && !drawAir) return 0;
        return scaledIconSize(scale);
    }

    static int scaledIconSize(float scale) {
        return Math.round(RenderDimensions.BASE_ICON_SIZE * normalizedScale(scale));
    }

    static float normalizedScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        return scale;
    }
}
