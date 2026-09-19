package me.ifair.luahud.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
                Text.empty(),
                onPress,
                DEFAULT_NARRATION_SUPPLIER
        );

        this.icon = icon;
    }

    @Override
    protected void renderWidget(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.renderWidget(context, mouseX, mouseY, delta);

        context.drawTexture(
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
