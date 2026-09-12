package me.ifair.luahud.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderLayoutEngineTest {
    private static final ElementWidthProvider TEXT_WIDTH = (element, sectionScale) -> RenderDimensions.scaleLength(
            ((RenderElement.Text) element).value().length(), sectionScale);
    private static final ElementHeightProvider TEXT_HEIGHT = (element, sectionScale) -> RenderDimensions.scaleLength(
            ((RenderElement.Text) element).value().length(), sectionScale);
    private final RenderLayoutEngine engine = new RenderLayoutEngine();

    @Test void rightAnchorKeepsRowsInsideTheScreen() {
        RenderSection section = section(Anchor.TOP_RIGHT, RowAlignment.RIGHT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text("123456"), true);
        section.add(text("12"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(88, elements.get(0).backgroundX());
        assertEquals(98, elements.get(0).backgroundX() + elements.get(0).backgroundWidth());
        assertEquals(92, elements.get(1).backgroundX());
        assertEquals(98, elements.get(1).backgroundX() + elements.get(1).backgroundWidth());
    }

    @Test void centerAnchorAlignsShortRowsWithinTheLongestRow() {
        assertEquals(45, shortRowX(RowAlignment.LEFT));
        assertEquals(47, shortRowX(RowAlignment.CENTER));
        assertEquals(49, shortRowX(RowAlignment.RIGHT));
    }

    @Test void inlineDirectionChangesVisualElementOrderWithoutMovingTheRow() {
        RenderSection leftToRight = section(Anchor.TOP_RIGHT, RowAlignment.RIGHT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        leftToRight.add(text("xx"), false);
        leftToRight.add(text("yyyy"), true);

        RenderSection rightToLeft = section(Anchor.TOP_RIGHT, RowAlignment.RIGHT,
                InlineDirection.RIGHT_TO_LEFT, RowDirection.TOP_TO_BOTTOM);
        rightToLeft.add(text("xx"), false);
        rightToLeft.add(text("yyyy"), true);

        List<RenderLayout.PositionedElement> normal = layout(leftToRight, 100, 60);
        List<RenderLayout.PositionedElement> reversed = layout(rightToLeft, 100, 60);

        assertEquals(88, normal.get(0).backgroundX());
        assertEquals(92, normal.get(1).backgroundX());
        assertEquals(94, reversed.get(0).backgroundX());
        assertEquals(88, reversed.get(1).backgroundX());
    }

    @Test void bottomAnchorKeepsBothRowDirectionsInsideTheScreen() {
        RenderSection topToBottom = section(Anchor.BOTTOM_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        topToBottom.add(text("x"), true);
        topToBottom.add(text("y"), true);

        RenderSection bottomToTop = section(Anchor.BOTTOM_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.BOTTOM_TO_TOP);
        bottomToTop.add(text("x"), true);
        bottomToTop.add(text("y"), true);

        List<RenderLayout.PositionedElement> normal = layout(topToBottom, 100, 60);
        List<RenderLayout.PositionedElement> reversed = layout(bottomToTop, 100, 60);

        assertEquals(34, normal.get(0).rowY());
        assertEquals(46, normal.get(1).rowY());
        assertEquals(46, reversed.get(0).rowY());
        assertEquals(34, reversed.get(1).rowY());
    }

    @Test void zeroWidthElementsDoNotStealOuterPadding() {
        RenderSection section = section(Anchor.TOP_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text(""), false);
        section.add(text("abc"), true);

        RenderLayout.PositionedElement visible = layout(section, 100, 60).getFirst();

        assertEquals(2, visible.backgroundX());
        assertEquals(4, visible.contentX());
        assertEquals(7, visible.backgroundWidth());
    }

    @Test void identicalSectionsRemainIndependentBlocks() {
        SectionLayout options = SectionLayout.defaults(Anchor.TOP_LEFT, 2, 2);
        RenderQueue queue = new RenderQueue();
        queue.beginSection(options);
        queue.add(text("first"), false);
        queue.beginSection(options);
        queue.add(text("second"), true);

        RenderLayout layout = engine.layout(queue.sections(), TEXT_WIDTH, TEXT_HEIGHT, 100, 60);

        assertEquals(2, layout.elements().size());
        assertEquals(2, layout.elements().get(0).rowY());
        assertEquals(2, layout.elements().get(1).rowY());
    }

    @Test void blankRowsStillAdvanceTheFollowingRow() {
        RenderSection section = section(Anchor.TOP_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text(""), true);
        section.add(text("visible"), true);

        RenderLayout.PositionedElement visible = layout(section, 100, 60).getFirst();

        assertEquals(14, visible.rowY());
    }

    @Test void sectionScaleAffectsWidthsPaddingAndRowHeight() {
        RenderSection section = section(Anchor.TOP_CENTER, RowAlignment.CENTER,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 2f, 10);
        section.add(text("123"), true);
        section.add(text("1"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(43, elements.get(0).backgroundX());
        assertEquals(14, elements.get(0).backgroundWidth());
        assertEquals(47, elements.get(0).contentX());
        assertEquals(2, elements.get(0).rowY());
        assertEquals(45, elements.get(1).backgroundX());
        assertEquals(22, elements.get(1).rowY());
        assertEquals(20, elements.get(1).rowHeight());
    }

    @Test void sectionScaleAffectsLocalElementOffsets() {
        RenderSection section = section(Anchor.TOP_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 2f, 12);
        section.add(new RenderElement.Text("x", 0, 0, false, 3, 2), true);

        RenderLayout.PositionedElement element = layout(section, 100, 60).getFirst();

        assertEquals(8, element.backgroundX());
        assertEquals(12, element.contentX());
        assertEquals(6, element.rowY());
    }

    @Test void automaticRowsShrinkAndGrowToTheirTallestElement() {
        RenderSection section = autoSection(Anchor.TOP_LEFT, RowDirection.TOP_TO_BOTTOM);
        section.add(text("123456"), false);
        section.add(text("12345678901234567890"), true);
        section.add(text("123456"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(20, elements.get(0).rowHeight());
        assertEquals(2, elements.get(0).rowY());
        assertEquals(20, elements.get(1).rowHeight());
        assertEquals(2, elements.get(1).rowY());
        assertEquals(6, elements.get(2).rowHeight());
        assertEquals(22, elements.get(2).rowY());
    }

    @Test void automaticEmptyRowsUseTheDefaultRowHeight() {
        RenderSection section = autoSection(Anchor.TOP_LEFT, RowDirection.TOP_TO_BOTTOM);
        section.newLine();
        section.add(text("123456"), true);

        RenderLayout.PositionedElement visible = layout(section, 100, 60).getFirst();

        assertEquals(14, visible.rowY());
        assertEquals(6, visible.rowHeight());
    }

    @Test void bottomToTopDirectionAccountsForDifferentAutomaticRowHeights() {
        RenderSection section = autoSection(Anchor.BOTTOM_LEFT, RowDirection.BOTTOM_TO_TOP);
        section.add(text("123456"), true);
        section.add(text("12345678901234567890"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(52, elements.get(0).rowY());
        assertEquals(32, elements.get(1).rowY());
    }

    private int shortRowX(RowAlignment alignment) {
        RenderSection section = section(Anchor.TOP_CENTER, alignment,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text("123456"), true);
        section.add(text("12"), true);
        return layout(section, 100, 60).get(1).backgroundX();
    }

    private List<RenderLayout.PositionedElement> layout(RenderSection section, int width, int height) {
        return engine.layout(List.of(section), TEXT_WIDTH, TEXT_HEIGHT, width, height).elements();
    }

    private static RenderSection section(
            Anchor anchor,
            RowAlignment alignment,
            InlineDirection inlineDirection,
            RowDirection rowDirection
    ) {
        return section(anchor, alignment, inlineDirection, rowDirection, 1f, 12);
    }

    private static RenderSection section(
            Anchor anchor,
            RowAlignment alignment,
            InlineDirection inlineDirection,
            RowDirection rowDirection,
            float scale,
            int rowHeight
    ) {
        return new RenderSection(new SectionLayout(anchor, anchor.defaultOffsetX(), 2,
                alignment, inlineDirection, rowDirection, scale, rowHeight, false));
    }

    private static RenderSection autoSection(Anchor anchor, RowDirection rowDirection) {
        return new RenderSection(new SectionLayout(anchor, anchor.defaultOffsetX(), 2,
                anchor.defaultRowAlignment(), InlineDirection.LEFT_TO_RIGHT, rowDirection,
                1f, 12, true));
    }

    private static RenderElement.Text text(String value) {
        return new RenderElement.Text(value, 0, 0, false, 0, 0);
    }
}
