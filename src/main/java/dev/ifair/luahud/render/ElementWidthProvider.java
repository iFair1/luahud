package dev.ifair.luahud.render;

@FunctionalInterface
interface ElementWidthProvider {
    int width(RenderElement element, float sectionScale);
}
