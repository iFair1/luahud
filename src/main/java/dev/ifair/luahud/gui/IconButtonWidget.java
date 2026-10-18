package dev.ifair.luahud.gui;

import dev.ifair.luahud.compat.GuiCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class IconButtonWidget extends ButtonWidget {
    private Identifier icon;
    private String label;

    public IconButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Identifier icon,
            String tooltip,
            PressAction onPress
    ) {
        super(
                x,
                y,
                width,
                height,
                Text.empty(),
                onPress,
                DEFAULT_NARRATION_SUPPLIER
        );

        this.icon = icon;
        this.label = tooltip;
        setTooltip(Tooltip.of(Text.literal(tooltip)));
    }

    @Override
    //? if <1.21.11 {
    protected void renderWidget(
    //?} else {
    /*protected void drawIcon(
    *///?}
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        //? if <1.21.11 {
        super.renderWidget(context, mouseX, mouseY, delta);
        //?} else {
        /*// Since 1.21.11, drawIcon must also draw the button background.
        drawButton(context);
        *///?}

        GuiCompat.drawTexture(context,
                icon,
                getX(),
                getY(),
                0.0F,
                0.0F,
                getWidth(),
                getHeight(),
                getWidth(),
                getHeight()
        );
    }

    public void setLabel(String label) {
        if (this.label.equals(label)) return;
        this.label = label;
        setTooltip(Tooltip.of(Text.literal(label)));
    }

    public void setIcon(Identifier icon) {
        this.icon = icon;
    }
}
