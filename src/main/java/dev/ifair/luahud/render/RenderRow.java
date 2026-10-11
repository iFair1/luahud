package dev.ifair.luahud.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RenderRow {
    private final List<RenderElement> elements = new ArrayList<>();

    void add(RenderElement element) {
        elements.add(element);
    }

    List<RenderElement> elements() {
        return Collections.unmodifiableList(elements);
    }
}
