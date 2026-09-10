package me.ifair.luahud.gui;

import me.ifair.luahud.error.ErrorEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ErrorListWidget extends ElementListWidget<ErrorListEntry> {
    private static final int CONTENT_MAX_WIDTH = 540;

    ErrorListWidget(
            MinecraftClient client,
            int width,
            int height,
            int y,
            int itemHeight,
            List<ErrorEntry> errors,
            Set<String> expandedGroups,
            LuaHUDErrorsScreen screen,
            Long selectedErrorId
    ) {
        super(client, width, height, y, itemHeight);
        Map<GroupKey, List<ErrorEntry>> groups = new LinkedHashMap<>();
        for (ErrorEntry error : errors) {
            GroupKey key = new GroupKey(error.group(), error.system());
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(error);
        }

        ErrorListEntry target = null;
        for (Map.Entry<GroupKey, List<ErrorEntry>> group : groups.entrySet()) {
            String expansionKey = (group.getKey().system ? "system:" : "profile:") + group.getKey().name;
            boolean expanded = expandedGroups.contains(expansionKey);
            this.addEntry(new ErrorListEntry.Group(
                    group.getKey().name, expansionKey, group.getValue().size(), expanded, screen));
            if (!expanded) continue;
            for (ErrorEntry error : group.getValue()) {
                boolean selected = selectedErrorId != null && error.id() == selectedErrorId;
                ErrorListEntry row = new ErrorListEntry.Error(error, client.textRenderer, selected);
                this.addEntry(row);
                if (selected) target = row;
            }
        }
        if (target != null) this.ensureVisible(target);
    }

    @Override
    public int getRowWidth() {
        return Math.min(CONTENT_MAX_WIDTH, this.getWidth() - 32);
    }

    @Override
    protected int getScrollbarX() {
        return this.getRight() - 8;
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
        if (this.getEntryCount() == 0) {
            context.drawCenteredTextWithShadow(
                    this.client.textRenderer,
                    Text.literal("No errors reported"),
                    this.getWidth() / 2,
                    this.getY() + (this.getHeight() - this.client.textRenderer.fontHeight) / 2,
                    0xAAAAAA
            );
        }
    }

    void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        ErrorListEntry hovered = this.getHoveredEntry();
        if (hovered instanceof ErrorListEntry.Error error) error.renderTooltip(context, mouseX, mouseY);
    }

    private record GroupKey(String name, boolean system) {
    }
}
