package me.ifair.luahud.render;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

sealed interface RenderElement permits RenderElement.Text, RenderElement.Stack, RenderElement.Item, RenderElement.Entity, RenderElement.Texture {
    int backgroundColor();
    int offsetX();
    int offsetY();

    record Text(
            String value,
            int textColor,
            int backgroundColor,
            boolean shadow,
            int offsetX,
            int offsetY
    ) implements RenderElement {}

    record Stack(
            ItemStack value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean drawAir
    ) implements RenderElement {}

    record Item(
            ItemStack value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY
    ) implements RenderElement {}

    record Entity(
            net.minecraft.entity.Entity value,
            float scale,
            float yaw,
            float pitch,
            boolean fullBright,
            boolean useEntityRotation,
            int backgroundColor,
            int offsetX,
            int offsetY
    ) implements RenderElement {}

    record Texture(
            Identifier value,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY
    ) implements RenderElement {}
}
