package dev.ifair.luahud.gui;

import dev.ifair.luahud.error.ErrorEntry;
import dev.ifair.luahud.error.ErrorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class ErrorListWidget extends ClickableWidget {
    // when enabled, it switches to single-pane layout if the screen width is low
    private static final boolean ENABLE_COMPACT_LAYOUT = false;
    enum Filter {
        ALL,
        PROFILES,
        SYSTEM
    }

    private static final int MOBILE_BREAKPOINT = 560;
    private static final int PANEL_GAP = 8;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_GAP = 1;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TextRenderer textRenderer;
    private final List<ErrorEntry> allErrors;
    private final Consumer<Long> selectionListener;
    private final List<ListRow> rows = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String query = "";
    private List<ErrorEntry> filteredErrors = List.of();
    @Nullable
    private Long selectedErrorId;
    private boolean mobileDetailVisible;
    private double listScroll;
    private double detailScroll;
    private int listContentsHeight;
    private int detailContentsHeight;

    ErrorListWidget(
            MinecraftClient client,
            int x,
            int y,
            int width,
            int height,
            List<ErrorEntry> errors,
            @Nullable Long selectedErrorId,
            boolean openedFromNotification,
            Consumer<Long> selectionListener
    ) {
        super(x, y, width, height, Text.literal("LuaHUD errors"));
        this.textRenderer = client.textRenderer;
        this.allErrors = List.copyOf(errors);
        this.selectedErrorId = selectedErrorId;
        this.mobileDetailVisible = ENABLE_COMPACT_LAYOUT
                && openedFromNotification
                && selectedErrorId != null
                && width < MOBILE_BREAKPOINT;
        this.selectionListener = selectionListener;
        this.rebuildFilteredErrors();
    }

    void setFilter(Filter filter, String query) {
        this.filter = filter;
        this.query = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        this.listScroll = 0;
        this.detailScroll = 0;
        this.rebuildFilteredErrors();
        this.keepSelectedVisible();
    }

    @Nullable
    Long selectedErrorId() {
        return this.selectedErrorId;
    }

    boolean isMobileDetailVisible() {
        return usesCompactLayout() && this.mobileDetailVisible;
    }

    boolean backToMobileList() {
        if (!isMobileDetailVisible()) return false;
        this.mobileDetailVisible = false;
        this.detailScroll = 0;
        return true;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.filteredErrors.isEmpty()) {
            renderEmpty(context);
            return;
        }

        if (usesCompactLayout()) {
            if (this.mobileDetailVisible && currentSelection() != null) {
                renderMobileDetail(context, mouseX, mouseY);
            } else {
                renderList(context, getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
            }
            return;
        }

        int listWidth = listWidth();
        renderList(context, getX(), getY(), listWidth, getHeight(), mouseX, mouseY);
        renderDetail(context, getX() + listWidth + PANEL_GAP, getY(),
                getWidth() - listWidth - PANEL_GAP, getHeight(), mouseX, mouseY, false);
    }

    @Override
    //? if <1.21.9 {
    public void onClick(double mouseX, double mouseY) {
    //?} else {
    /*public void onClick(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
    *///?}
        if (isMobileDetailVisible()) {
            if (mouseY < getY() + 27) backToMobileList();
            return;
        }

        int listWidth = usesCompactLayout()
                ? getWidth()
                : listWidth();
        if (mouseX >= getX() + listWidth) return;

        ListRow row = rowAt(mouseY);
        if (row == null) return;
        select(row.error().id());
        if (usesCompactLayout()) this.mobileDetailVisible = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        double movement = -verticalAmount * 34;

        if (usesCompactLayout()) {
            if (this.mobileDetailVisible) scrollDetail(movement, getHeight() - 27);
            else scrollList(movement, getHeight());
            return true;
        }

        int listWidth = listWidth();
        if (mouseX < getX() + listWidth) scrollList(movement, getHeight());
        else scrollDetail(movement, getHeight());
        return true;
    }

    @Override
    //? if <1.21.9 {
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //?} else {
    /*public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
    *///?}
        if (keyCode == GLFW.GLFW_KEY_ENTER && usesCompactLayout() && this.selectedErrorId != null) {
            this.mobileDetailVisible = true;
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_UP && keyCode != GLFW.GLFW_KEY_DOWN) return false;
        if (this.filteredErrors.isEmpty()) return true;

        int currentIndex = -1;
        for (int i = 0; i < this.filteredErrors.size(); i++) {
            if (this.filteredErrors.get(i).id() == this.selectedErrorId) {
                currentIndex = i;
                break;
            }
        }
        int offset = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
        int nextIndex = Math.clamp(currentIndex + offset, 0, this.filteredErrors.size() - 1);
        select(this.filteredErrors.get(nextIndex).id());
        keepSelectedVisible();
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    private void rebuildFilteredErrors() {
        Comparator<ErrorEntry> newestFirst = Comparator.comparingLong(ErrorEntry::lastSeen).reversed();
        this.filteredErrors = this.allErrors.stream()
                .filter(this::matchesFilter)
                .sorted(Comparator.comparing(ErrorEntry::system).reversed().thenComparing(newestFirst))
                .toList();

        if (this.selectedErrorId == null
                || this.filteredErrors.stream().noneMatch(error -> error.id() == this.selectedErrorId)) {
            this.selectedErrorId = this.filteredErrors.isEmpty() ? null : this.filteredErrors.getFirst().id();
            this.mobileDetailVisible = false;
            this.selectionListener.accept(this.selectedErrorId);
        }
        rebuildRows();
    }

    private boolean matchesFilter(ErrorEntry error) {
        if (this.filter == Filter.PROFILES && error.system()) return false;
        if (this.filter == Filter.SYSTEM && !error.system()) return false;
        if (this.query.isEmpty()) return true;

        String haystack = String.join("\n", error.source(), error.phase(), error.displayMessage())
                .toLowerCase(Locale.ROOT);
        return haystack.contains(this.query);
    }

    private void rebuildRows() {
        this.rows.clear();
        int top = 4;
        for (ErrorEntry error : this.filteredErrors) {
            this.rows.add(new ListRow(error, top));
            top += ROW_HEIGHT + ROW_GAP;
        }
        this.listContentsHeight = top + 5;
        this.listScroll = Math.min(this.listScroll, maxListScroll(getHeight()));
    }

    private void renderList(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        drawPanel(context, x, y, width, height);
        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        for (ListRow row : this.rows) {
            ErrorEntry current = current(row.error());
            int rowY = y + row.top() - (int) this.listScroll;
            if (rowY + ROW_HEIGHT < y || rowY > y + height) continue;

            boolean selected = this.selectedErrorId != null && current.id() == this.selectedErrorId;
            boolean hovered = mouseX >= x + 4 && mouseX < x + width - 4
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int rowColor = selected ? 0x99505050 : hovered ? 0x66383838 : 0x33181818;
            context.fill(x + 4, rowY, x + width - 5, rowY + ROW_HEIGHT, rowColor);

            int textX = x + 9;
            int availableWidth = width - (textX - x) - 12;
            String source = this.textRenderer.trimToWidth(current.source(), Math.max(30, availableWidth - 54));
            context.drawTextWithShadow(this.textRenderer, source, textX, rowY + 6, 0xFFFFFFFF);

            String phase = current.phase();
            phase = this.textRenderer.trimToWidth(phase, 50);
            context.drawTextWithShadow(this.textRenderer, phase,
                    x + width - 10 - this.textRenderer.getWidth(phase), rowY + 6, 0xFFAAAAAA);

            int countWidth = current.count() > 1 ? this.textRenderer.getWidth("×" + current.count()) + 8 : 0;
            String summary = this.textRenderer.trimToWidth(current.summary(), Math.max(20, availableWidth - countWidth));
            context.drawTextWithShadow(this.textRenderer, summary, textX, rowY + 20, 0xFFAAAAAA);
            if (current.count() > 1) {
                String count = "×" + current.count();
                context.drawTextWithShadow(this.textRenderer, count,
                        x + width - 10 - this.textRenderer.getWidth(count), rowY + 20, 0xFFAAAAAA);
            }
        }

        renderScrollbar(context, x + width - 4, y + 4, height - 8,
                this.listScroll, this.listContentsHeight, height);
        context.disableScissor();
    }

    private void renderMobileDetail(DrawContext context, int mouseX, int mouseY) {
        drawPanel(context, getX(), getY(), getWidth(), getHeight());
        boolean hovered = mouseX >= getX() && mouseX < getRight()
                && mouseY >= getY() && mouseY < getY() + 27;
        if (hovered) context.fill(getX() + 1, getY() + 1, getRight() - 1, getY() + 27, 0x552F3138);
        context.drawTextWithShadow(this.textRenderer, "‹  All errors", getX() + 10, getY() + 9, 0xFFE0E0E4);
        context.fill(getX() + 1, getY() + 27, getRight() - 1, getY() + 28, 0xFF444444);
        renderDetail(context, getX(), getY() + 28, getWidth(), getHeight() - 28, mouseX, mouseY, true);
    }

    private void renderDetail(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean panelAlreadyDrawn
    ) {
        ErrorEntry selected = currentSelection();
        if (!panelAlreadyDrawn) drawPanel(context, x, y, width, height);
        if (selected == null) return;

        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int contentX = x + 12;
        int contentWidth = Math.max(40, width - 24);
        int cursor = y + 12 - (int) this.detailScroll;

        String title = this.textRenderer.trimToWidth(selected.source(), contentWidth);
        context.drawTextWithShadow(this.textRenderer, title, contentX, cursor, 0xFFFFFFFF);
        cursor += 15;

        String location = selected.line().equals("—") ? "Location unavailable" : "Line " + selected.line();
        String kind = selected.system() ? "LuaHUD" : "Profile";
        String metadata = kind + " · " + selected.phase() + " · " + location;
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(metadata, contentWidth), contentX, cursor, 0xFFAAAAAA);
        cursor += 18;

        context.fill(contentX, cursor, contentX + contentWidth, cursor + 1, 0xFF555555);
        cursor += 10;

        List<OrderedText> messageLines = wrapMessage(selected.displayMessage(), contentWidth);
        for (int i = 0; i < messageLines.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, messageLines.get(i),
                    contentX, cursor + i * 11, 0xFFDDDDDD);
        }
        cursor += Math.max(1, messageLines.size()) * 11 + 10;

        context.fill(contentX, cursor, contentX + contentWidth, cursor + 1, 0xFF555555);
        cursor += 10;
        context.drawTextWithShadow(this.textRenderer, "Occurrences: " + selected.count(),
                contentX, cursor, 0xFFAAAAAA);
        cursor += 13;
        context.drawTextWithShadow(this.textRenderer,
                "First seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(selected.firstSeen())),
                contentX, cursor, 0xFFAAAAAA);
        cursor += 13;
        context.drawTextWithShadow(this.textRenderer,
                "Last seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(selected.lastSeen())),
                contentX, cursor, 0xFFAAAAAA);
        cursor += 20;

        this.detailContentsHeight = cursor - y + (int) this.detailScroll;
        this.detailScroll = Math.min(this.detailScroll, maxDetailScroll(height));
        renderScrollbar(context, x + width - 4, y + 4, height - 8,
                this.detailScroll, this.detailContentsHeight, height);
        context.disableScissor();
    }

    private void renderEmpty(DrawContext context) {
        drawPanel(context, getX(), getY(), getWidth(), getHeight());
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2 - 6;
        boolean trulyEmpty = this.allErrors.isEmpty();
        String title = trulyEmpty ? "No errors reported" : "No matching errors";
        String detail = trulyEmpty ? "" : "Try another filter or search.";
        context.drawCenteredTextWithShadow(this.textRenderer, title, centerX, centerY, 0xFFDDDDDD);
        context.drawCenteredTextWithShadow(this.textRenderer, detail, centerX, centerY + 15, 0xFF888888);
    }

    private void select(long id) {
        if (this.selectedErrorId != null && this.selectedErrorId == id) return;
        this.selectedErrorId = id;
        this.detailScroll = 0;
        this.selectionListener.accept(id);
    }

    @Nullable
    private ErrorEntry currentSelection() {
        if (this.selectedErrorId == null) return null;
        return ErrorManager.find(this.selectedErrorId)
                .orElseGet(() -> this.filteredErrors.stream()
                        .filter(error -> error.id() == this.selectedErrorId)
                        .findFirst()
                        .orElse(null));
    }

    private static ErrorEntry current(ErrorEntry error) {
        return ErrorManager.find(error.id()).orElse(error);
    }

    @Nullable
    private ListRow rowAt(double mouseY) {
        double contentY = mouseY - getY() + this.listScroll;
        for (ListRow row : this.rows) {
            if (contentY >= row.top() && contentY < row.top() + ROW_HEIGHT) return row;
        }
        return null;
    }

    private void keepSelectedVisible() {
        if (this.selectedErrorId == null) return;
        for (ListRow row : this.rows) {
            if (row.error().id() != this.selectedErrorId) continue;
            if (row.top() < this.listScroll) this.listScroll = row.top();
            double visibleBottom = this.listScroll + getHeight();
            if (row.top() + ROW_HEIGHT > visibleBottom) {
                this.listScroll = row.top() + ROW_HEIGHT - getHeight();
            }
            return;
        }
    }

    private void scrollList(double amount, int viewportHeight) {
        this.listScroll = Math.clamp(this.listScroll + amount, 0, maxListScroll(viewportHeight));
    }

    private void scrollDetail(double amount, int viewportHeight) {
        this.detailScroll = Math.clamp(this.detailScroll + amount, 0, maxDetailScroll(viewportHeight));
    }

    private int maxListScroll(int viewportHeight) {
        return Math.max(0, this.listContentsHeight - viewportHeight);
    }

    private int maxDetailScroll(int viewportHeight) {
        return Math.max(0, this.detailContentsHeight - viewportHeight);
    }

    private boolean usesCompactLayout() {
        return ENABLE_COMPACT_LAYOUT && getWidth() < MOBILE_BREAKPOINT;
    }

    private int listWidth() {
        int minimum = Math.min(224, Math.max(120, getWidth() / 2));
        return Math.min(284, Math.max(minimum, (int) (getWidth() * 0.38)));
    }

    private List<OrderedText> wrapMessage(String message, int width) {
        List<OrderedText> result = new ArrayList<>();
        for (String line : message.split("\\n", -1)) {
            List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(line), width);
            if (wrapped.isEmpty()) result.add(Text.empty().asOrderedText());
            else result.addAll(wrapped);
        }
        return result;
    }

    private static void drawPanel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0x99000000);
        context.fill(x, y, x + width, y + 1, 0xFF555555);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
        context.fill(x, y + 1, x + 1, y + height - 1, 0xFF555555);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF555555);
    }

    private static void renderScrollbar(
            DrawContext context,
            int x,
            int y,
            int height,
            double scroll,
            int contentHeight,
            int viewportHeight
    ) {
        if (contentHeight <= viewportHeight || height <= 0) return;
        int thumbHeight = Math.max(16, height * viewportHeight / contentHeight);
        int travel = height - thumbHeight;
        int maxScroll = contentHeight - viewportHeight;
        int thumbY = y + (int) Math.round(travel * scroll / maxScroll);
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x44000000);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
    }

    private record ListRow(ErrorEntry error, int top) {
    }
}
