package dev.ifair.luahud.gui;

import dev.ifair.luahud.compat.GuiCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;

public class IconButtonWidget extends ButtonWidget {
    private Identifier icon;

    public IconButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Identifier icon,
            PressAction onPress
    ) {
        super(
                x,
                y,
                width,
                height,
                net.minecraft.text.Text.empty(),
                onPress,
                DEFAULT_NARRATION_SUPPLIER
        );

        this.icon = icon;
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

    public void setIcon(Identifier icon) {
        this.icon = icon;
    }
}
