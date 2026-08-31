package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;

public class ProfileListContainer {
    public static FlowLayout create() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(70));
        panel.surface(Surface.VANILLA_TRANSLUCENT);
        panel.padding(Insets.of(8));

        // Scrollable list container
        FlowLayout scrollContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        scrollContent.gap(4);

        // schizophrenia placeholders
        scrollContent.child(ProfileEntryComponent.create("I'm a placeholder", true, false, true));
        scrollContent.child(ProfileEntryComponent.create("I am too! We should be friends", false, false, false));
        scrollContent.child(ProfileEntryComponent.create("Hey! That's my friend >:(", false, true, true));

        ScrollContainer<FlowLayout> scrollContainer = Containers.verticalScroll(
                Sizing.fill(100),
                Sizing.fill(100),
                scrollContent
        );

        panel.child(scrollContainer);

        return panel;
    }
}
