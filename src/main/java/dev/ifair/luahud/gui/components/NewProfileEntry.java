package dev.ifair.luahud.gui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class NewProfileEntry extends ProfileListEntry {
    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 16;

    private final ButtonWidget button;

    public NewProfileEntry(ProfileListWidget list) {
        // Text label = Text.literal("+ ").formatted(Formatting.GREEN).
           //     append(Text.literal("New").formatted(Formatting.WHITE));
        Text label = Text.empty()
                .append(Text.literal("+ ").formatted(Formatting.GREEN))
                .append(Text.literal("New"));
        this.button = ButtonWidget.builder(label, ignored -> list.createProfile())
                .dimensions(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
    }

    @Override
    //? if <1.21.9 {
    public void render(
            DrawContext context,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float tickDelta
    ) {
    //?} else {
    /*public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();
        int entryWidth = getContentWidth();
        int entryHeight = getContentHeight();
    *///?}
        button.setPosition(x, y);
        button.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public List<? extends Element> children() {
        return List.of(button);
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of(button);
    }
}
