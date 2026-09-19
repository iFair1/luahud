package me.ifair.luahud.error;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ErrorNotifications {
    private static final int MAX_HOVER_LENGTH = 1000;

    private ErrorNotifications() {
    }

    public static void flush(MinecraftClient client) {
        if (client.player == null) return;
        for (ErrorEntry error : ErrorManager.drainNotifications()) {
            String detail = error.displayMessage();
            String preview = detail.length() <= MAX_HOVER_LENGTH
                    ? detail
                    : detail.substring(0, MAX_HOVER_LENGTH) + "...";
            MutableText view = Text.literal("View").formatted(Formatting.WHITE, Formatting.UNDERLINE).styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/luahud errors " + error.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(preview))));
            MutableText message = Text.literal("[LuaHUD] ").formatted(Formatting.RED)
                    .append(Text.literal("[" + error.phase() + "] error at " + error.source() + ": ")
                            .formatted(Formatting.WHITE))
                    .append(view);
            client.player.sendMessage(message, false);
        }
    }
}
