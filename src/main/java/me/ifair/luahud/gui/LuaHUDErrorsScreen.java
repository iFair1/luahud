package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LuaHUDErrorsScreen extends Screen {
    private static final int CONTENT_MAX_WIDTH = 760;
    private static final int PAGE_MARGIN = 12;
    private static final int HEADER_HEIGHT = 42;
    private static final int TOOLBAR_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 34;
    private static final DateTimeFormatter COPY_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Nullable
    private final Screen parent;
    @Nullable
    private final Long requestedErrorId;
    private ErrorListWidget.Filter filter = ErrorListWidget.Filter.ALL;
    private String query = "";
    @Nullable
    private Long selectedErrorId;
    private ErrorListWidget errorBrowser;
    private TextFieldWidget searchField;
    private ButtonWidget allButton;
    private ButtonWidget profileButton;
    private ButtonWidget systemButton;
    private ButtonWidget copyButton;
    private ButtonWidget dismissButton;
    private long displayedRevision;

    public LuaHUDErrorsScreen(@Nullable Screen parent, @Nullable Long selectedErrorId) {
        super(Text.literal("LuaHUD Errors"));
        this.parent = parent;
        this.requestedErrorId = selectedErrorId;
        this.selectedErrorId = selectedErrorId;
    }

    @Override
    protected void init() {
        List<ErrorEntry> errors = ErrorManager.snapshot();
        this.displayedRevision = ErrorManager.structureRevision();

        int contentWidth = Math.max(1, Math.min(CONTENT_MAX_WIDTH, this.width - PAGE_MARGIN * 2));
        int contentX = (this.width - contentWidth) / 2;
        int toolbarY = HEADER_HEIGHT;
        int filterWidth = Math.min(78, Math.max(54, (contentWidth - 122) / 5));
        int searchX = contentX + filterWidth * 3 + 8;
        int searchWidth = Math.max(54, contentX + contentWidth - searchX);

        this.allButton = this.addDrawableChild(filterButton(
                Text.literal("All"), contentX, toolbarY, filterWidth, ErrorListWidget.Filter.ALL));
        this.profileButton = this.addDrawableChild(filterButton(
                Text.literal("Profiles"), contentX + filterWidth, toolbarY, filterWidth,
                ErrorListWidget.Filter.PROFILES));
        this.systemButton = this.addDrawableChild(filterButton(
                Text.literal("LuaHUD"), contentX + filterWidth * 2, toolbarY, filterWidth,
                ErrorListWidget.Filter.SYSTEM));

        this.searchField = this.addDrawableChild(new TextFieldWidget(
                this.textRenderer, searchX, toolbarY, searchWidth, 20, Text.literal("Search errors")));
        this.searchField.setMaxLength(160);
        this.searchField.setPlaceholder(Text.literal("Search messages...").formatted(Formatting.DARK_GRAY));
        this.searchField.setText(this.query);

        int browserY = toolbarY + TOOLBAR_HEIGHT;
        int browserHeight = Math.max(40, this.height - browserY - FOOTER_HEIGHT);
        Long initialSelection = findInitialSelection(errors);
        this.errorBrowser = this.addDrawableChild(new ErrorListWidget(
                this.client,
                contentX,
                browserY,
                contentWidth,
                browserHeight,
                errors,
                initialSelection,
                this.requestedErrorId != null,
                this::selectError
        ));
        this.errorBrowser.setFilter(this.filter, this.query);
        this.selectedErrorId = this.errorBrowser.selectedErrorId();

        this.searchField.setChangedListener(value -> {
            this.query = value;
            this.errorBrowser.setFilter(this.filter, value);
            this.selectedErrorId = this.errorBrowser.selectedErrorId();
            updateSelectionButtons();
        });

        int footerY = this.height - 28;
        int buttonWidth = Math.min(126, Math.max(1, (contentWidth - 16) / 3));
        this.copyButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Copy details"), button -> copySelectedError())
                .dimensions(contentX, footerY, buttonWidth, 20)
                .build());
        this.dismissButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Dismiss"), button -> dismissSelectedError())
                .dimensions(contentX + buttonWidth + 8, footerY, buttonWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(contentX + contentWidth - buttonWidth, footerY, buttonWidth, 20)
                .build());

        updateFilterButtons();
        updateSelectionButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (ErrorManager.structureRevision() != this.displayedRevision) this.clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);

        int errorCount = ErrorManager.snapshot().size();
        String status = errorCount == 1 ? "1 error" : errorCount + " errors";
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, 27, 0xFFAAAAAA);
    }

    @Override
    //? if <1.21.9 {
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //?} else {
    /*public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
    *///?}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.errorBrowser != null
                && this.errorBrowser.backToMobileList()) return true;
        //? if <1.21.9 {
        return super.keyPressed(keyCode, scanCode, modifiers);
        //?} else {
        /*return super.keyPressed(input);
        *///?}
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    private ButtonWidget filterButton(Text label, int x, int y, int width, ErrorListWidget.Filter target) {
        return ButtonWidget.builder(label, button -> {
                    this.filter = target;
                    this.errorBrowser.setFilter(target, this.query);
                    this.selectedErrorId = this.errorBrowser.selectedErrorId();
                    updateFilterButtons();
                    updateSelectionButtons();
                })
                .dimensions(x, y, width, 20)
                .build();
    }

    private void selectError(@Nullable Long id) {
        this.selectedErrorId = id;
        if (this.copyButton != null) this.copyButton.setMessage(Text.literal("Copy details"));
        updateSelectionButtons();
    }

    private void updateFilterButtons() {
        if (this.allButton == null) return;
        this.allButton.active = this.filter != ErrorListWidget.Filter.ALL;
        this.profileButton.active = this.filter != ErrorListWidget.Filter.PROFILES;
        this.systemButton.active = this.filter != ErrorListWidget.Filter.SYSTEM;
    }

    private void updateSelectionButtons() {
        boolean hasSelection = currentSelection() != null;
        if (this.copyButton != null) this.copyButton.active = hasSelection;
        if (this.dismissButton != null) this.dismissButton.active = hasSelection;
    }

    private void dismissSelectedError() {
        if (this.selectedErrorId == null) return;
        ErrorManager.dismiss(this.selectedErrorId);
        this.clearAndInit();
    }

    private void copySelectedError() {
        ErrorEntry error = currentSelection();
        if (error == null || this.client == null) return;

        String location = error.line().equals("—") ? error.source() : error.source() + ":" + error.line();
        String details = "LuaHUD error\n"
                + "Source: " + location + "\n"
                + "Phase: " + error.phase() + "\n"
                + "Occurrences: " + error.count() + "\n"
                + "First seen: " + COPY_TIME_FORMAT.format(Instant.ofEpochMilli(error.firstSeen())) + "\n"
                + "Last seen: " + COPY_TIME_FORMAT.format(Instant.ofEpochMilli(error.lastSeen())) + "\n\n"
                + error.displayMessage();
        this.client.keyboard.setClipboard(details);
        this.copyButton.setMessage(Text.literal("Copied!"));
    }

    @Nullable
    private ErrorEntry currentSelection() {
        if (this.selectedErrorId == null) return null;
        return ErrorManager.find(this.selectedErrorId).orElse(null);
    }

    @Nullable
    private Long findInitialSelection(List<ErrorEntry> errors) {
        if (this.selectedErrorId != null
                && errors.stream().anyMatch(error -> error.id() == this.selectedErrorId)) {
            return this.selectedErrorId;
        }
        return errors.isEmpty() ? null : errors.getFirst().id();
    }
}
