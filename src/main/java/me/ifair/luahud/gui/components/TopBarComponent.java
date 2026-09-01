package me.ifair.luahud.gui.components;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.ifair.luahud.LuaHUD;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

public class TopBarComponent {
    private static final String GITHUB_URL = "https://github.com/iFair1/luahud";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/luahud";
    // dont have discord yet

    public static FlowLayout create() {
        FlowLayout topBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(25));
        topBar.verticalAlignment(VerticalAlignment.CENTER);

        // Links (left)
        FlowLayout linksLayout = Containers.horizontalFlow(Sizing.fill(33),Sizing.content());
        linksLayout.gap(2);
        linksLayout.padding(Insets.left(2));
        linksLayout.child(Components.button(Text.literal("Gh"), button -> {
            openInBrowser(GITHUB_URL);
        }));
        linksLayout.child(Components.button(Text.literal("Md"), button -> {
            openInBrowser(MODRINTH_URL);
        }));
        linksLayout.child(Components.button(Text.literal("Dc"), button -> {
            // TODO: open a discord server :)
        }));

        FlowLayout centerLayout = Containers.horizontalFlow(Sizing.fill(34), Sizing.content());
        centerLayout.verticalAlignment(VerticalAlignment.CENTER);
        centerLayout.horizontalAlignment(HorizontalAlignment.CENTER);
        centerLayout.child(Components.label(Text.literal("LuaHUD")).shadow(true));

        // Other buttons (right)
        FlowLayout rightLayout = Containers.horizontalFlow(Sizing.fill(33), Sizing.content());
        rightLayout.horizontalAlignment(HorizontalAlignment.RIGHT);
        rightLayout.verticalAlignment(VerticalAlignment.CENTER);
        rightLayout.padding(Insets.right(2));
        rightLayout.child(Components.button(Text.literal("SH"), buttonComponent -> {
            // TODO
        }));

        topBar.child(linksLayout);
        topBar.child(centerLayout);
        topBar.child(rightLayout);

        return topBar;
    }

    private static void openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while directing to url \"{}\": {}", url, e.getMessage());
        }
    }
}
