package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LuaHUDErrorsScreen extends Screen {
    private static final int CONTENT_MAX_WIDTH = 540;
    private static final int ROW_HEIGHT = 28;

    @Nullable
    private final Screen parent;
    @Nullable
    private final Long selectedErrorId;
    private final Set<Long> expandedErrors = new HashSet<>();
    private boolean targetPending;
    private ErrorListWidget errorList;
    private long displayedRevision;

    public LuaHUDErrorsScreen(@Nullable Screen parent, @Nullable Long selectedErrorId) {
        super(Text.literal("LuaHUD Errors"));
        this.parent = parent;
        this.selectedErrorId = selectedErrorId;
        this.targetPending = selectedErrorId != null;
        if (selectedErrorId != null) this.expandedErrors.add(selectedErrorId);
    }

    @Override
    protected void init() {
        List<ErrorEntry> errors = ErrorManager.snapshot();
        this.displayedRevision = ErrorManager.structureRevision();
        this.errorList = this.addDrawableChild(new ErrorListWidget(
                this.client,
                this.width,
                Math.max(ROW_HEIGHT, this.height - 72),
                36,
                errors,
                this.expandedErrors,
                this.targetPending ? this.selectedErrorId : null
        ));
        this.targetPending = false;

        int buttonWidth = Math.min(150, Math.min(CONTENT_MAX_WIDTH, this.width - 24));
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.close())
                .dimensions((this.width - buttonWidth) / 2, this.height - 28, buttonWidth, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (ErrorManager.structureRevision() != this.displayedRevision) this.clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 22, 0xFFFFFF);
        this.errorList.renderTooltip(context, mouseX, mouseY);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

}
