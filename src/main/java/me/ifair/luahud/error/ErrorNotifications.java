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
            String preview = error.message().length() <= MAX_HOVER_LENGTH
                    ? error.message()
                    : error.message().substring(0, MAX_HOVER_LENGTH) + "...";
            MutableText message = Text.literal("[LuaHUD] ").formatted(Formatting.RED)
                    .append(Text.literal(error.group() + ": " + error.phase() + " error: View")
                            .formatted(Formatting.WHITE));
            message.styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/luahud errors " + error.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(preview))));
            client.player.sendMessage(message, false);
        }
    }
}
