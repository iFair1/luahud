package dev.ifair.luahud.render;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

sealed interface RenderElement permits RenderElement.Text, RenderElement.Stack, RenderElement.Item, RenderElement.Entity, RenderElement.Texture, RenderElement.Canvas {
    default float opacity() { return 1; }
    int backgroundColor();
    int offsetX();
    int offsetY();

    record Canvas(CanvasDrawing drawing, int offsetX, int offsetY) implements RenderElement {
        @Override public int backgroundColor() { return 0; }
    }

    record Text(
            String value,
            int textColor,
            int backgroundColor,
            boolean shadow,
            int offsetX,
            int offsetY,
            float opacity
    ) implements RenderElement {
        Text(String value, int textColor, int backgroundColor, boolean shadow, int offsetX, int offsetY) {
            this(value, textColor, backgroundColor, shadow, offsetX, offsetY, 1);
        }
    }

    record Stack(
            ItemStack value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean drawAir,
            float opacity
    ) implements RenderElement {
        Stack(ItemStack value, float scale, int backgroundColor, int offsetX, int offsetY, boolean drawAir) {
            this(value, scale, backgroundColor, offsetX, offsetY, drawAir, 1);
        }
    }

    record Item(
            ItemStack value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            float opacity
    ) implements RenderElement {
        Item(ItemStack value, float scale, int backgroundColor, int offsetX, int offsetY) {
            this(value, scale, backgroundColor, offsetX, offsetY, 1);
        }
    }

    record Entity(
            net.minecraft.entity.Entity value,
            float scale,
            float yaw,
            float pitch,
            boolean fullBright,
            boolean useEntityRotation,
            int backgroundColor,
            int offsetX,
            int offsetY,
            float opacity
    ) implements RenderElement {
        Entity(net.minecraft.entity.Entity value, float scale, float yaw, float pitch, boolean fullBright, boolean useEntityRotation, int backgroundColor, int offsetX, int offsetY) {
            this(value, scale, yaw, pitch, fullBright, useEntityRotation, backgroundColor, offsetX, offsetY, 1);
        }
    }

    record Texture(
            Identifier value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            float opacity
    ) implements RenderElement {
        Texture(Identifier value, float scale, int backgroundColor, int offsetX, int offsetY) {
            this(value, scale, backgroundColor, offsetX, offsetY, 1);
        }
    }
}
