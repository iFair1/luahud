package dev.ifair.luahud.gui;

import dev.ifair.luahud.LuaHUD;
import net.minecraft.util.Identifier;

public class GuiTextures {

    private GuiTextures() {}

    public static final Identifier GITHUB = id("textures/logo/github.png");
    public static final Identifier MODRINTH = id("textures/logo/modrinth.png");
    public static final Identifier DISCORD = id("textures/logo/discord.png");

    public static final Identifier ENABLED = id("textures/16/enabled.png");
    public static final Identifier DISABLED = id("textures/16/disabled.png");
    public static final Identifier HIDDEN = id("textures/16/eye_closed.png");
    public static final Identifier SHOWN = id("textures/16/eye.png");
    public static final Identifier MOVE_UP = id("textures/16/up.png");
    public static final Identifier MOVE_DOWN = id("textures/16/down.png");
    public static final Identifier DELETE = id("textures/16/trash.png");
    public static final Identifier DELETE_CONFIRM = id("textures/16/trash_red.png");

    public static final Identifier NO_ERRORS = id("textures/20/exclamation.png");
    public static final Identifier ERRORS = id("textures/20/exclamation_red.png");
    public static final Identifier SHOW_HIDDEN = id("textures/20/eye_closed.png");
    public static final Identifier HIDE_HIDDEN = id("textures/20/eye.png");


    // this here because i like my hands
    private static Identifier id(String path) {
        return Identifier.of(LuaHUD.MOD_ID, path);
    }
}
