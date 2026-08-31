package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;

public class ProfileEntryComponent {
    public static FlowLayout create(String profileName, boolean isSelected, boolean isHidden) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(28));
        row.surface(Surface.PANEL);
        row.padding(Insets.horizontal(8));
        row.verticalAlignment(VerticalAlignment.CENTER);

        // checkbox
        var checkbox = Components.checkbox(Text.empty());
        checkbox.checked(isSelected);
        row.child(checkbox);

        // Name of the profile
        FlowLayout nameBox = Containers.horizontalFlow(Sizing.fixed(180), Sizing.content());
        nameBox.padding(Insets.of(4));
        nameBox.margins(Insets.horizontal(8));
        nameBox.child(Components.label(Text.literal(profileName)));
        row.child(nameBox);

        // Other buttons
        FlowLayout actionsLayout = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actionsLayout.horizontalAlignment(HorizontalAlignment.RIGHT);
        actionsLayout.verticalAlignment(VerticalAlignment.CENTER);
        actionsLayout.gap(4);

        var hideBtn = Components.button(Text.literal("H"), button -> {
            // TODO
        });
        hideBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));

        var editBtn = Components.button(Text.literal("E"), button -> {
            // TODO
        });
        editBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));

        actionsLayout.child(hideBtn);
        actionsLayout.child(editBtn);

        row.child(actionsLayout);

        return row;
    }
}
