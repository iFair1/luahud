package me.ifair.luahud.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RenderQueue {
    private final List<RenderSection> sections = new ArrayList<>();
    private RenderSection currentSection;

    void clear() {
        sections.clear();
        currentSection = null;
    }

    void beginSection(SectionLayout layout) {
        currentSection = new RenderSection(layout);
        sections.add(currentSection);
    }

    void add(RenderElement element, boolean endsRow) {
        if (currentSection == null) {
            throw new IllegalStateException("Cannot add a render element before beginning a section");
        }
        currentSection.add(element, endsRow);
    }

    List<RenderSection> sections() {
        return Collections.unmodifiableList(sections);
    }
}
