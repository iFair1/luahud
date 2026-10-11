package dev.ifair.luahud.render;

@FunctionalInterface
interface ElementHeightProvider {
    int height(RenderElement element, float sectionScale);
}
