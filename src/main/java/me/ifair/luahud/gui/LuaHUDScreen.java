package me.ifair.luahud.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.ifair.luahud.gui.components.BottomBarComponent;
import me.ifair.luahud.gui.components.ProfileListContainer;
import me.ifair.luahud.gui.components.TopBarComponent;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaHUDScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;

    public LuaHUDScreen(@Nullable Screen parent) {
        this.parent = parent;
    }

    // This will be used when the user opens this via in game keybind
    public LuaHUDScreen() {
        this(null);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.
                surface(Surface.OPTIONS_BACKGROUND)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        rootComponent.child(TopBarComponent.create());
        rootComponent.child(ProfileListContainer.create());
        rootComponent.child(BottomBarComponent.create(this));
    }
}
