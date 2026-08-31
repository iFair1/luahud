package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;

public class ProfileEntryComponent {
    final static int PADDING = 50;
    final static int ROW_HEIGHT = 16;
    final static int GAP = 2;

    public static FlowLayout create(String profileName, boolean isSelected, boolean isHidden, boolean hasError) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));

        row.padding(Insets.horizontal(PADDING));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(GAP);

        // checkbox
        var checkbox = Components.button(Text.literal(isSelected ? "S" : "U"), button -> {
            // TODO
        });
        checkbox.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(checkbox);


        // Name of the profile (Label/Input box)
        StackLayout profileContainer = Containers.stack(Sizing.expand(100), Sizing.fill(100));
        profileContainer.horizontalAlignment(HorizontalAlignment.LEFT);
        profileContainer.verticalAlignment(VerticalAlignment.CENTER);

        var nameLabel = Components.label(Text.literal(profileName));
        nameLabel.shadow(true);
        nameLabel.margins(Insets.of(1, 0, 5, 0));
        profileContainer.child(nameLabel);

        row.child(profileContainer);


        // Other buttons
        var hideBtn = Components.button(Text.literal(isHidden ? "Show" : "Hide"), button -> {
            // TODO
        });
        hideBtn.sizing(Sizing.fixed(2 * ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(hideBtn);

        var keybindBtn = Components.button(Text.literal("Keybindddd"), button -> {
            // TODO
        });
        keybindBtn.sizing(Sizing.fixed(4 * ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(keybindBtn);

        var editBtn = Components.button(Text.literal("E"), button -> {
            // TODO
        });
        editBtn.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(editBtn);

        var errorBtn = Components.button(Text.literal("!"), button -> {
            // TODO
        });
        errorBtn.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        errorBtn.visible = hasError;
        // errorBtn.margins(Insets.right(-PADDING));
        row.child(errorBtn);

        return row;
    }
}
