package me.ifair.luahud.render;

import java.util.List;

record RenderLayout(List<PositionedElement> elements) {
    RenderLayout {
        elements = List.copyOf(elements);
    }

    record PositionedElement(
            RenderElement element,
            Anchor anchor,
            int backgroundX,
            int rowY,
            int contentX,
            int backgroundWidth,
            float sectionScale,
            int rowHeight
    ) {}
}
