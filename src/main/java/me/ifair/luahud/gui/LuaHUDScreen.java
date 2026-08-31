package me.ifair.luahud.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.ifair.luahud.gui.components.BottomBarComponent;
import me.ifair.luahud.gui.components.ProfileListContainer;
import me.ifair.luahud.gui.components.TopBarComponent;
import org.jetbrains.annotations.NotNull;

public class LuaHUDScreen extends BaseOwoScreen<FlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.
                surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        rootComponent.child(TopBarComponent.create());
        rootComponent.child(ProfileListContainer.create());
        rootComponent.child(BottomBarComponent.create());
    }
}
