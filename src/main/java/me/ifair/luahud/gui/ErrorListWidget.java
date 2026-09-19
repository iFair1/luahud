package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class ErrorListWidget extends ClickableWidget {
    enum Filter {
        ALL,
        PROFILES,
        SYSTEM
    }

    private static final int MOBILE_BREAKPOINT = 560;
    private static final int PANEL_GAP = 8;
    private static final int ROW_HEIGHT = 48;
    private static final int ROW_GAP = 3;
    private static final int GROUP_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FULL_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
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
        this.mobileDetailVisible = openedFromNotification && selectedErrorId != null && width < MOBILE_BREAKPOINT;
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
        return this.getWidth() < MOBILE_BREAKPOINT && this.mobileDetailVisible;
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

        if (this.getWidth() < MOBILE_BREAKPOINT) {
            if (this.mobileDetailVisible && currentSelection() != null) {
                renderMobileDetail(context, mouseX, mouseY);
            } else {
                renderList(context, getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
            }
            return;
        }

        int listWidth = Math.clamp((int) (getWidth() * 0.38), 224, 284);
        renderList(context, getX(), getY(), listWidth, getHeight(), mouseX, mouseY);
        renderDetail(context, getX() + listWidth + PANEL_GAP, getY(),
                getWidth() - listWidth - PANEL_GAP, getHeight(), mouseX, mouseY, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (isMobileDetailVisible()) {
            if (mouseY < getY() + 27) backToMobileList();
            return;
        }

        int listWidth = getWidth() < MOBILE_BREAKPOINT
                ? getWidth()
                : Math.clamp((int) (getWidth() * 0.38), 224, 284);
        if (mouseX >= getX() + listWidth) return;

        ListRow row = rowAt(mouseY);
        if (row == null) return;
        select(row.error().id());
        if (getWidth() < MOBILE_BREAKPOINT) this.mobileDetailVisible = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        double movement = -verticalAmount * 34;

        if (getWidth() < MOBILE_BREAKPOINT) {
            if (this.mobileDetailVisible) scrollDetail(movement, getHeight() - 27);
            else scrollList(movement, getHeight());
            return true;
        }

        int listWidth = Math.clamp((int) (getWidth() * 0.38), 224, 284);
        if (mouseX < getX() + listWidth) scrollList(movement, getHeight());
        else scrollDetail(movement, getHeight());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && getWidth() < MOBILE_BREAKPOINT && this.selectedErrorId != null) {
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
        int top = 7;
        boolean previousSystem = false;
        boolean first = true;
        for (ErrorEntry error : this.filteredErrors) {
            if (first || error.system() != previousSystem) {
                top += GROUP_HEIGHT;
                previousSystem = error.system();
                first = false;
            }
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

        Boolean previousSystem = null;
        for (ListRow row : this.rows) {
            ErrorEntry current = current(row.error());
            int rowY = y + row.top() - (int) this.listScroll;
            if (previousSystem == null || current.system() != previousSystem) {
                int groupY = rowY - GROUP_HEIGHT + 5;
                String group = current.system() ? "LUAHUD" : "PROFILES";
                context.drawTextWithShadow(this.textRenderer, group, x + 9, groupY,
                        current.system() ? 0xFFFF8090 : 0xFF8FA9FF);
                previousSystem = current.system();
            }
            if (rowY + ROW_HEIGHT < y || rowY > y + height) continue;

            boolean selected = this.selectedErrorId != null && current.id() == this.selectedErrorId;
            boolean hovered = mouseX >= x + 4 && mouseX < x + width - 4
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int rowColor = selected ? 0xDD34232A : hovered ? 0xCC282A31 : 0xA81C1E24;
            context.fill(x + 4, rowY, x + width - 5, rowY + ROW_HEIGHT, rowColor);
            if (selected) context.fill(x + 4, rowY, x + 7, rowY + ROW_HEIGHT, 0xFFFF667A);

            int textX = x + (selected ? 12 : 10);
            int availableWidth = width - (textX - x) - 12;
            String source = this.textRenderer.trimToWidth(current.source(), Math.max(30, availableWidth - 54));
            context.drawTextWithShadow(this.textRenderer, source, textX, rowY + 7, 0xFFF1F1F3);

            String phase = current.phase().toUpperCase(Locale.ROOT);
            phase = this.textRenderer.trimToWidth(phase, 50);
            context.drawTextWithShadow(this.textRenderer, phase,
                    x + width - 10 - this.textRenderer.getWidth(phase), rowY + 7, 0xFFCF7B88);

            int countWidth = current.count() > 1 ? this.textRenderer.getWidth("×" + current.count()) + 8 : 0;
            String summary = this.textRenderer.trimToWidth(current.summary(), Math.max(20, availableWidth - countWidth));
            context.drawTextWithShadow(this.textRenderer, summary, textX, rowY + 21, 0xFFB8B8C0);
            if (current.count() > 1) {
                String count = "×" + current.count();
                context.drawTextWithShadow(this.textRenderer, count,
                        x + width - 10 - this.textRenderer.getWidth(count), rowY + 21, 0xFFFF9AA6);
            }

            String line = current.line().equals("—") ? "No line" : "Line " + current.line();
            String seen = relativeTime(current.lastSeen());
            context.drawTextWithShadow(this.textRenderer, line, textX, rowY + 34, 0xFF777984);
            context.drawTextWithShadow(this.textRenderer, seen,
                    x + width - 10 - this.textRenderer.getWidth(seen), rowY + 34, 0xFF777984);
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
        context.fill(getX() + 1, getY() + 27, getRight() - 1, getY() + 28, 0xFF30323A);
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
        int contentX = x + 14;
        int contentWidth = Math.max(40, width - 28);
        int cursor = y + 14 - (int) this.detailScroll;

        String kind = selected.system() ? "SYSTEM ERROR" : "PROFILE ERROR";
        int kindColor = selected.system() ? 0xFFFF7184 : 0xFF8FA9FF;
        context.drawTextWithShadow(this.textRenderer, kind, contentX, cursor, kindColor);
        cursor += 16;

        String title = this.textRenderer.trimToWidth(selected.source(), contentWidth);
        context.drawTextWithShadow(this.textRenderer, title, contentX, cursor, 0xFFFFFFFF);
        cursor += 14;

        String location = selected.line().equals("—") ? "Location unavailable" : "Line " + selected.line();
        location = this.textRenderer.trimToWidth(location, Math.max(20, contentWidth / 2 - 4));
        String phase = this.textRenderer.trimToWidth(
                "Phase: " + selected.phase(), Math.max(20, contentWidth / 2 - 4));
        context.drawTextWithShadow(this.textRenderer, location, contentX, cursor, 0xFF9698A3);
        context.drawTextWithShadow(this.textRenderer, phase,
                contentX + contentWidth - this.textRenderer.getWidth(phase), cursor, 0xFFCF8993);
        cursor += 18;

        context.fill(contentX, cursor, contentX + contentWidth, cursor + 1, 0xFF363842);
        cursor += 12;
        context.drawTextWithShadow(this.textRenderer, "MESSAGE", contentX, cursor, 0xFF7F818D);
        cursor += 15;

        List<OrderedText> messageLines = wrapMessage(selected.displayMessage(), Math.max(20, contentWidth - 18));
        int messageHeight = Math.max(34, messageLines.size() * 11 + 16);
        context.fill(contentX, cursor, contentX + contentWidth, cursor + messageHeight, 0xD9121419);
        context.drawBorder(contentX, cursor, contentWidth, messageHeight, 0xFF30323B);
        for (int i = 0; i < messageLines.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, messageLines.get(i),
                    contentX + 9, cursor + 8 + i * 11, 0xFFE0E0E4);
        }
        cursor += messageHeight + 13;

        context.drawTextWithShadow(this.textRenderer, "ACTIVITY", contentX, cursor, 0xFF7F818D);
        cursor += 15;
        int cardGap = 5;
        int cardWidth = Math.max(1, (contentWidth - cardGap * 2) / 3);
        renderStat(context, contentX, cursor, cardWidth, "OCCURRENCES", Integer.toString(selected.count()));
        renderStat(context, contentX + cardWidth + cardGap, cursor, cardWidth,
                "FIRST SEEN", FULL_TIME_FORMAT.format(Instant.ofEpochMilli(selected.firstSeen())));
        renderStat(context, contentX + (cardWidth + cardGap) * 2, cursor,
                contentWidth - (cardWidth + cardGap) * 2,
                "LAST SEEN", FULL_TIME_FORMAT.format(Instant.ofEpochMilli(selected.lastSeen())));
        cursor += 45;

        int noteColor = selected.system() ? 0xFFB28D92 : 0xFF8797BE;
        String note = selected.system()
                ? "This error comes from LuaHUD itself. Copy the details when reporting it."
                : "Edit the profile and save it. LuaHUD reloads profiles automatically.";
        List<OrderedText> noteLines = this.textRenderer.wrapLines(Text.literal(note), contentWidth - 18);
        int noteHeight = noteLines.size() * 11 + 16;
        context.fill(contentX, cursor, contentX + contentWidth, cursor + noteHeight,
                selected.system() ? 0x332F2024 : 0x33212A40);
        context.fill(contentX, cursor, contentX + 2, cursor + noteHeight, noteColor);
        for (int i = 0; i < noteLines.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, noteLines.get(i),
                    contentX + 10, cursor + 8 + i * 11, 0xFFBFC0C7);
        }
        cursor += noteHeight + 14;

        this.detailContentsHeight = cursor - y + (int) this.detailScroll;
        this.detailScroll = Math.min(this.detailScroll, maxDetailScroll(height));
        renderScrollbar(context, x + width - 4, y + 4, height - 8,
                this.detailScroll, this.detailContentsHeight, height);
        context.disableScissor();
    }

    private void renderStat(DrawContext context, int x, int y, int width, String label, String value) {
        context.fill(x, y, x + width, y + 37, 0xB51A1C22);
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(label, Math.max(1, width - 10)), x + 6, y + 6, 0xFF71737D);
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth(value, Math.max(1, width - 10)), x + 6, y + 20, 0xFFD5D5DA);
    }

    private void renderEmpty(DrawContext context) {
        drawPanel(context, getX(), getY(), getWidth(), getHeight());
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2 - 16;
        boolean trulyEmpty = this.allErrors.isEmpty();
        int accent = trulyEmpty ? 0xFF55D68B : 0xFF8FA9FF;

        context.drawBorder(centerX - 11, centerY - 11, 22, 22, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, trulyEmpty ? "✓" : "?", centerX, centerY - 4, accent);
        String title = trulyEmpty ? "No errors reported" : "No matching errors";
        String detail = trulyEmpty ? "LuaHUD is running quietly." : "Try another filter or search.";
        context.drawCenteredTextWithShadow(this.textRenderer, title, centerX, centerY + 20, 0xFFF1F1F3);
        context.drawCenteredTextWithShadow(this.textRenderer, detail, centerX, centerY + 35, 0xFF858791);
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
        context.fill(x, y, x + width, y + height, 0xD90E1015);
        context.drawBorder(x, y, width, height, 0xFF30323A);
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
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x442F3139);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF747681);
    }

    private static String relativeTime(long epochMillis) {
        Duration elapsed = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now());
        long seconds = Math.max(0, elapsed.toSeconds());
        if (seconds < 60) return "now";
        if (seconds < 3600) return seconds / 60 + "m ago";
        if (seconds < 86400) return seconds / 3600 + "h ago";
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private record ListRow(ErrorEntry error, int top) {
    }
}
