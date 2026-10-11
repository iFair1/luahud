package dev.ifair.luahud.render;

import java.util.List;

record RenderLayout(List<PositionedElement> elements) {
    RenderLayout {
        elements = List.copyOf(elements);
    }

    record PositionedElement(
            RenderElement element,
            int backgroundX,
            int backgroundY,
            int rowY,
            int contentY,
            int contentX,
            int backgroundWidth,
            float sectionScale,
            int rowHeight
    ) {}
}
