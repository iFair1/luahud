package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ErrorListWidget extends ScrollableWidget {
    private static final int CONTENT_MAX_WIDTH = 540;
    private static final int COLLAPSED_HEIGHT = 28;
    private static final int ROW_GAP = 2;
    private static final int CONTENT_PADDING = 2;
    private static final int DETAIL_LINE_HEIGHT = 10;
    private static final int DETAIL_HEADER_HEIGHT = 17;
    private static final int DETAIL_FOOTER_HEIGHT = 15;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TextRenderer textRenderer;
    private final List<ErrorEntry> errors;
    private final Set<Long> expandedErrors;
    private final Long selectedErrorId;
    private final int rowWidth;
    private final int rowX;
    private final Columns columns;
    private final List<Row> rows = new ArrayList<>();
    private int contentsHeight;

    ErrorListWidget(
            MinecraftClient client,
            int width,
            int height,
            int y,
            List<ErrorEntry> errors,
            Set<Long> expandedErrors,
            Long selectedErrorId
    ) {
        super(0, y, Math.max(1, width - 8), height, Text.literal("LuaHUD errors"));
        this.textRenderer = client.textRenderer;
        this.errors = List.copyOf(errors);
        this.expandedErrors = expandedErrors;
        this.selectedErrorId = selectedErrorId;
        this.rowWidth = Math.min(CONTENT_MAX_WIDTH, width - 32);
        this.rowX = (width - this.rowWidth) / 2;
        this.columns = Columns.fit(this.textRenderer, this.errors, this.rowWidth);
        this.rebuildRows();
        this.scrollToSelected();
    }

    @Override
    protected int getContentsHeight() {
        return this.contentsHeight;
    }

    @Override
    protected double getDeltaYPerScroll() {
        return COLLAPSED_HEIGHT;
    }

    @Override
    protected void drawBox(DrawContext context) {
    }

    @Override
    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.rows.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No errors reported"),
                    this.getWidth() / 2,
                    this.getY() + (this.getHeight() - this.textRenderer.fontHeight) / 2,
                    0xAAAAAA
            );
            return;
        }

        Row hovered = this.rowAt(mouseX, mouseY);
        for (Row row : this.rows) this.renderRow(context, row, row == hovered);
    }

    void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        Row hovered = this.rowAt(mouseX, mouseY);
        if (hovered == null) return;

        ErrorEntry current = current(hovered.error);
        context.drawTooltip(this.textRenderer, List.of(
                Text.literal("First seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(current.firstSeen()))),
                Text.literal("Last seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(current.lastSeen()))),
                Text.literal("Occurrences: " + current.count())
        ), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Row row = this.rowAt(mouseX, mouseY);
            if (row != null && row.expandable) {
                if (!this.expandedErrors.add(row.error.id())) this.expandedErrors.remove(row.error.id());
                boolean expanding = this.expandedErrors.contains(row.error.id());
                double previousScroll = this.getScrollY();
                this.rebuildRows();
                this.setScrollY(previousScroll);
                if (expanding) this.keepVisible(row.error.id());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    private void rebuildRows() {
        this.rows.clear();
        int top = this.getY() + CONTENT_PADDING;
        int detailWidth = Math.max(40, this.rowWidth - 24);
        for (ErrorEntry error : this.errors) {
            boolean expandable = error.displayMessage().contains("\n")
                    || this.textRenderer.getWidth(error.summary()) > this.columns.messageWidth();
            boolean expanded = expandable && this.expandedErrors.contains(error.id());
            List<OrderedText> lines = expanded
                    ? wrapMessage(this.textRenderer, error.displayMessage(), detailWidth)
                    : List.of();
            int height = expanded
                    ? DETAIL_HEADER_HEIGHT + lines.size() * DETAIL_LINE_HEIGHT + DETAIL_FOOTER_HEIGHT
                    : COLLAPSED_HEIGHT;
            boolean selected = this.selectedErrorId != null && error.id() == this.selectedErrorId;
            this.rows.add(new Row(error, top, height, expandable, expanded, selected, lines));
            top += height + ROW_GAP;
        }
        this.contentsHeight = Math.max(0, top - this.getY() - ROW_GAP + CONTENT_PADDING);
    }

    private void renderRow(DrawContext context, Row row, boolean hovered) {
        ErrorEntry current = current(row.error);
        int x = this.rowX;
        int y = row.top;
        int background = row.selected ? 0x66552222 : hovered ? 0x44333333 : 0x22000000;
        context.fill(x, y, x + this.rowWidth, y + row.height, background);
        if (row.expanded) context.fill(x, y, x + 2, y + row.height, 0xFFAA5555);

        int contentX = x + 7;
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(current.line(), this.columns.lineWidth() - 3),
                contentX, y + 4, 0x999999);
        contentX += this.columns.lineWidth();
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(current.source(), this.columns.sourceWidth() - 4),
                contentX, y + 4, 0xFFFFFF);
        contentX += this.columns.sourceWidth();
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(current.phase(), this.columns.phaseWidth() - 4),
                contentX, y + 4, 0xFF7777);
        contentX += this.columns.phaseWidth();

        if (!row.expanded) {
            String message = this.textRenderer.trimToWidth(
                    current.summary(), this.columns.visibleMessageWidth(row.expandable));
            context.drawTextWithShadow(this.textRenderer, message, contentX, y + 4, 0xDDDDDD);
            this.renderSeen(context, current, contentX, y + 15);
        } else {
            int messageX = x + 12;
            int messageY = y + DETAIL_HEADER_HEIGHT;
            for (int i = 0; i < row.detailLines.size(); i++) {
                context.drawTextWithShadow(this.textRenderer, row.detailLines.get(i),
                        messageX, messageY + i * DETAIL_LINE_HEIGHT, 0xDDDDDD);
            }
            this.renderSeen(context, current, messageX,
                    messageY + row.detailLines.size() * DETAIL_LINE_HEIGHT + 1);
        }

        if (row.expandable) {
            context.drawTextWithShadow(this.textRenderer, row.expanded ? "▼" : "▶",
                    x + this.rowWidth - 13, y + 4, 0xBBBBBB);
        }
    }

    private void renderSeen(DrawContext context, ErrorEntry error, int x, int y) {
        String seen = "×" + error.count() + "  " + TIME_FORMAT.format(Instant.ofEpochMilli(error.lastSeen()));
        context.drawTextWithShadow(this.textRenderer, seen, x, y, 0x888888);
    }

    private Row rowAt(double mouseX, double mouseY) {
        if (!this.isWithinBounds(mouseX, mouseY)
                || mouseX < this.rowX || mouseX >= this.rowX + this.rowWidth) return null;
        double contentY = mouseY + this.getScrollY();
        for (Row row : this.rows) {
            if (contentY >= row.top && contentY < row.top + row.height) return row;
        }
        return null;
    }

    private void scrollToSelected() {
        if (this.selectedErrorId == null) return;
        this.rows.stream()
                .filter(row -> row.error.id() == this.selectedErrorId)
                .findFirst()
                .ifPresent(row -> this.setScrollY(row.top - this.getY() - this.getHeight() / 2.0));
    }

    private void keepVisible(long id) {
        this.rows.stream().filter(row -> row.error.id() == id).findFirst().ifPresent(row -> {
            double bottom = row.top + row.height - this.getScrollY();
            double visibleBottom = this.getY() + this.getHeight() - CONTENT_PADDING;
            if (bottom > visibleBottom) this.setScrollY(this.getScrollY() + bottom - visibleBottom);
        });
    }

    private static ErrorEntry current(ErrorEntry error) {
        return ErrorManager.find(error.id()).orElse(error);
    }

    private static List<OrderedText> wrapMessage(TextRenderer renderer, String message, int width) {
        List<OrderedText> result = new ArrayList<>();
        for (String line : message.split("\\n", -1)) {
            List<OrderedText> wrapped = renderer.wrapLines(Text.literal(line), width);
            if (wrapped.isEmpty()) result.add(Text.empty().asOrderedText());
            else result.addAll(wrapped);
        }
        return result;
    }

    private record Row(
            ErrorEntry error,
            int top,
            int height,
            boolean expandable,
            boolean expanded,
            boolean selected,
            List<OrderedText> detailLines
    ) {
    }

    private record Columns(int lineWidth, int sourceWidth, int phaseWidth, int messageWidth) {
        private static final int LEFT_PADDING = 7;
        private static final int RIGHT_PADDING = 5;
        private static final int EXPAND_CONTROL_WIDTH = 15;
        private static final int MIN_SOURCE_WIDTH = 42;
        private static final int MIN_PHASE_WIDTH = 24;
        private static final int MIN_MESSAGE_WIDTH = 64;

        private static Columns fit(TextRenderer renderer, List<ErrorEntry> errors, int rowWidth) {
            int lineWidth = errors.stream().mapToInt(error -> renderer.getWidth(error.line())).max().orElse(0) + 5;
            int sourceWidth = errors.stream().mapToInt(error -> renderer.getWidth(error.source())).max().orElse(0) + 7;
            int phaseWidth = errors.stream().mapToInt(error -> renderer.getWidth(error.phase())).max().orElse(0) + 7;

            lineWidth = Math.min(32, lineWidth);
            sourceWidth = Math.min(126, Math.max(MIN_SOURCE_WIDTH, sourceWidth));
            phaseWidth = Math.min(126, Math.max(MIN_PHASE_WIDTH, phaseWidth));

            int availableForColumns = Math.max(0,
                    rowWidth - LEFT_PADDING - RIGHT_PADDING - MIN_MESSAGE_WIDTH);
            int overflow = lineWidth + sourceWidth + phaseWidth - availableForColumns;
            if (overflow > 0) {
                int sourceReduction = Math.min(overflow, sourceWidth - MIN_SOURCE_WIDTH);
                sourceWidth -= sourceReduction;
                overflow -= sourceReduction;
            }
            if (overflow > 0) {
                int phaseReduction = Math.min(overflow, phaseWidth - MIN_PHASE_WIDTH);
                phaseWidth -= phaseReduction;
            }

            int messageWidth = Math.max(10,
                    rowWidth - LEFT_PADDING - RIGHT_PADDING - lineWidth - sourceWidth - phaseWidth);
            return new Columns(lineWidth, sourceWidth, phaseWidth, messageWidth);
        }

        private int visibleMessageWidth(boolean expandable) {
            return Math.max(10, this.messageWidth - (expandable ? EXPAND_CONTROL_WIDTH : 0));
        }
    }
}
