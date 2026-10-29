package dev.ifair.luahud.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RenderSection {
    private final SectionLayout layout;
    private final List<RenderRow> rows = new ArrayList<>();
    private RenderRow currentRow;
    private RenderElement.Canvas canvas;

    RenderElement.Canvas canvas() { return canvas; }
    void canvas(RenderElement.Canvas value) { canvas = value; }

    RenderSection(SectionLayout layout) {
        this.layout = layout;
    }

    SectionLayout layout() {
        return layout;
    }

    List<RenderRow> rows() {
        return Collections.unmodifiableList(rows);
    }

    void add(RenderElement element, boolean endsRow) {
        if (currentRow == null) {
            currentRow = new RenderRow();
            rows.add(currentRow);
        }
        currentRow.add(element);
        if (endsRow) currentRow = null;
    }

    void newLine() {
        if (currentRow == null) rows.add(new RenderRow());
        currentRow = null;
    }
}
