package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;

public class ProfileEntryComponent {
    final static int PADDING = 8;
    final static int ROW_HEIGHT = 20;
    final static int GAP = 2;

    public static FlowLayout create(String profileName, boolean isSelected, boolean isHidden, boolean hasError) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));

        row.padding(Insets.horizontal(PADDING));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(GAP);

        // checkbox
        var checkbox = Components.checkbox(Text.empty());
        checkbox.checked(isSelected);
        checkbox.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(checkbox);



        // Name of the profile (Label/Input box)
        StackLayout profileContainer = Containers.stack(Sizing.expand(100), Sizing.fill(100));
        profileContainer.horizontalAlignment(HorizontalAlignment.LEFT);
        profileContainer.verticalAlignment(VerticalAlignment.CENTER);

        var nameLabel = Components.label(Text.literal(profileName));
        nameLabel.shadow(true);

        var nameInput = Components.textBox(Sizing.fill(100));
        nameInput.text(profileName);
        nameInput.sizing(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));

        // Change the label text when input box changes
        nameInput.onChanged().subscribe(newValue -> nameLabel.text(Text.literal(newValue)));

        profileContainer.child(nameLabel);

        profileContainer.mouseEnter().subscribe(() -> {
            profileContainer.clearChildren();
            profileContainer.child(nameInput);
        });

        profileContainer.mouseLeave().subscribe(() -> {
            if (!nameInput.isActive()) {
                profileContainer.clearChildren();
                profileContainer.child(nameLabel);
            }
        });

        row.child(profileContainer);



        // Other buttons
        var hideBtn = Components.button(Text.literal(isHidden ? "Show" : "Hide"), button -> {
            // TODO
        });
        hideBtn.sizing(Sizing.fixed(3 * ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(hideBtn);

        var keybindBtn = Components.button(Text.literal("Keybindddd"), button -> {
            // TODO
        });
        keybindBtn.sizing(Sizing.fixed(6 * ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(keybindBtn);

        var editBtn = Components.button(Text.literal("E"), button -> {
            // TODO
        });
        editBtn.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
        row.child(editBtn);

        if (hasError) {
            var errorBtn = Components.button(Text.literal("!"), button -> {
                // TODO
            });
            errorBtn.sizing(Sizing.fixed(ROW_HEIGHT), Sizing.fixed(ROW_HEIGHT));
            row.child(errorBtn);
        }

        return row;
    }
}
