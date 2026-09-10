package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

abstract class ErrorListEntry extends ElementListWidget.Entry<ErrorListEntry> {
    static final class Group extends ErrorListEntry {
        private final ButtonWidget button;

        Group(String name, String expansionKey, int count, boolean expanded, LuaHUDErrorsScreen screen) {
            String arrow = expanded ? "▼ " : "▶ ";
            this.button = ButtonWidget.builder(
                    Text.literal(arrow + name + " (" + count + ")"),
                    ignored -> screen.toggleGroup(expansionKey)
            ).dimensions(0, 0, 100, 22).build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            this.button.setDimensionsAndPosition(entryWidth, entryHeight - 4, x, y + 2);
            this.button.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(this.button);
        }
    }

    static final class Error extends ErrorListEntry {
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        private final ErrorEntry error;
        private final TextRenderer textRenderer;
        private final boolean selected;

        Error(ErrorEntry error, TextRenderer textRenderer, boolean selected) {
            this.error = error;
            this.textRenderer = textRenderer;
            this.selected = selected;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            ErrorEntry current = current();
            int background = this.selected ? 0x66552222 : hovered ? 0x44333333 : 0x22000000;
            context.fill(x, y, x + entryWidth, y + entryHeight, background);

            String prefix = current.phase() + ": ";
            int prefixWidth = this.textRenderer.getWidth(prefix);
            context.drawTextWithShadow(this.textRenderer, prefix, x + 6, y + 4, 0xFF7777);
            String message = this.textRenderer.trimToWidth(current.message(), Math.max(10, entryWidth - prefixWidth - 12));
            context.drawTextWithShadow(this.textRenderer, message, x + 6 + prefixWidth, y + 4, 0xFFFFFF);

            String seen = "×" + current.count() + "  " + TIME_FORMAT.format(Instant.ofEpochMilli(current.lastSeen()));
            context.drawTextWithShadow(this.textRenderer, seen, x + 6, y + 15, 0x999999);
        }

        void renderTooltip(DrawContext context, int mouseX, int mouseY) {
            ErrorEntry current = current();
            Text details = Text.literal(current.message()
                    + "\nFirst seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(current.firstSeen()))
                    + "\nLast seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(current.lastSeen()))
                    + "\nOccurrences: " + current.count());
            context.drawTooltip(this.textRenderer, details, mouseX, mouseY);
        }

        private ErrorEntry current() {
            return ErrorManager.find(this.error.id()).orElse(this.error);
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of();
        }
    }
}
