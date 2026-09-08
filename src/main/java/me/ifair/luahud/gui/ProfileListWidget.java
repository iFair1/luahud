package me.ifair.luahud.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;

final class ProfileListWidget extends ElementListWidget<ProfileEntryWidget> {
    private final TextRenderer textRenderer;

    ProfileListWidget(
            MinecraftClient client,
            int width,
            int height,
            int y,
            int itemHeight,
            List<ProfileEntry> profiles,
            TextRenderer textRenderer,
            LuaHUDScreen screen
    ) {
        super(client, width, height, y, itemHeight);
        this.textRenderer = textRenderer;
        profiles.forEach(profile -> this.addEntry(new ProfileEntryWidget(profile, client, textRenderer, screen)));
    }

    @Override
    public int getRowWidth() {
        return Math.min(LuaHUDScreen.CONTENT_MAX_WIDTH, this.getWidth() - 32);
    }

    @Override
    protected int getScrollbarX() {
        return this.getRight() - 8;
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
        if (this.getEntryCount() == 0) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No Lua profiles found"),
                    this.getWidth() / 2,
                    this.getY() + (this.getHeight() - this.textRenderer.fontHeight) / 2,
                    0xAAAAAA
            );
        }
    }
}
