package dev.ifair.luahud.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertEquals(30, normal.get(0).rowY());
        assertEquals(44, normal.get(1).rowY());
        assertEquals(44, reversed.get(0).rowY());
        assertEquals(30, reversed.get(1).rowY());
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

        assertEquals(16, visible.rowY());
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
        assertEquals(26, elements.get(1).rowY());
        assertEquals(24, elements.get(1).rowHeight());
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

        assertEquals(22, elements.get(0).rowHeight());
        assertEquals(2, elements.get(0).rowY());
        assertEquals(22, elements.get(1).rowHeight());
        assertEquals(2, elements.get(1).rowY());
        assertEquals(8, elements.get(2).rowHeight());
        assertEquals(24, elements.get(2).rowY());
    }

    @Test void inlineAlignmentPositionsShortElementsWithinTheTallestElement() {
        assertEquals(2, shortElementY(InlineAlignment.TOP));
        assertEquals(9, shortElementY(InlineAlignment.CENTER));
        assertEquals(16, shortElementY(InlineAlignment.BOTTOM));
    }

    @Test void automaticEmptyRowsUseTheDefaultRowHeight() {
        RenderSection section = autoSection(Anchor.TOP_LEFT, RowDirection.TOP_TO_BOTTOM);
        section.newLine();
        section.add(text("123456"), true);

        RenderLayout.PositionedElement visible = layout(section, 100, 60).getFirst();

        assertEquals(16, visible.rowY());
        assertEquals(8, visible.rowHeight());
    }

    @Test void bottomToTopDirectionAccountsForDifferentAutomaticRowHeights() {
        RenderSection section = autoSection(Anchor.BOTTOM_LEFT, RowDirection.BOTTOM_TO_TOP);
        section.add(text("123456"), true);
        section.add(text("12345678901234567890"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(50, elements.get(0).rowY());
        assertEquals(28, elements.get(1).rowY());
    }

    @Test void lineSpacingFavorsTheTopWithoutChangingTheDistanceBetweenRows() {
        RenderSection section = new RenderSection(new SectionLayout(
                Anchor.TOP_LEFT, 2, 2, RowAlignment.LEFT, InlineAlignment.CENTER,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 1f, 12, false, 3));
        section.add(text("x"), true);
        section.add(text("y"), true);

        List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);

        assertEquals(0, elements.get(0).backgroundY());
        assertEquals(15, elements.get(0).rowHeight());
        assertEquals(17, elements.get(1).rowY());
        assertEquals(15, elements.get(1).backgroundY());
    }

    @Test void defaultLineSpacingMovesBothPixelsAboveTheRow() {
        RenderSection section = section(Anchor.TOP_LEFT, RowAlignment.LEFT,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text("x"), true);

        RenderLayout.PositionedElement element = layout(section, 100, 60).getFirst();

        assertEquals(0, element.backgroundY());
        assertEquals(14, element.rowHeight());
    }

    @Test void inlineSpacingOnlyFillsGapsAndRespectsAnchorsDirectionsAndScale() {
        for (Anchor anchor : Anchor.values()) {
            for (InlineDirection direction : InlineDirection.values()) {
                for (float scale : new float[] {0.5f, 1f, 1.5f, 2f}) {
                    SectionLayout options = new SectionLayout(anchor, anchor.defaultOffsetX(), 2,
                            anchor.defaultRowAlignment(), InlineAlignment.CENTER, direction,
                            RowDirection.TOP_TO_BOTTOM, scale, 12, false, 2, 2);
                    RenderSection section = new RenderSection(options);
                    section.add(text("xx"), false);
                    section.add(text("yyyy"), true);

                    List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);
                    int spacing = RenderDimensions.scaleLength(2, scale);
                    int padding = RenderDimensions.scaleOffset(2, scale);
                    int firstWidth = RenderDimensions.scaleLength(2, scale);
                    int secondWidth = RenderDimensions.scaleLength(4, scale);
                    int rowWidth = firstWidth + secondWidth + 2 * padding + spacing;
                    int rowX = anchor.resolveX(100, rowWidth, anchor.defaultOffsetX());
                    RenderLayout.PositionedElement first = elements.get(0);
                    RenderLayout.PositionedElement second = elements.get(1);

                    assertEquals(firstWidth + padding + spacing, first.backgroundWidth());
                    assertEquals(secondWidth + padding, second.backgroundWidth());
                    if (direction == InlineDirection.LEFT_TO_RIGHT) {
                        assertEquals(rowX, first.backgroundX());
                        assertEquals(first.backgroundX() + first.backgroundWidth(), second.backgroundX());
                        assertEquals(rowX + rowWidth, second.backgroundX() + second.backgroundWidth());
                        assertEquals(spacing, second.contentX() - first.contentX() - firstWidth);
                    } else {
                        assertEquals(rowX, second.backgroundX());
                        assertEquals(second.backgroundX() + second.backgroundWidth(), first.backgroundX());
                        assertEquals(rowX + rowWidth, first.backgroundX() + first.backgroundWidth());
                        assertEquals(spacing, first.contentX() - second.contentX() - secondWidth);
                    }
                }
            }
        }
    }

    @Test void inlineSpacingCountsOnlyVisibleElementsWhenAligningRows() {
        for (RowAlignment alignment : RowAlignment.values()) {
            RenderSection section = new RenderSection(new SectionLayout(
                    Anchor.TOP_CENTER, 0, 2, alignment, InlineAlignment.CENTER,
                    InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 1f, 12, true, 2, 2));
            section.add(text(""), false);
            section.add(text("xx"), false);
            section.add(text(""), false);
            section.add(text("yyyy"), false);
            section.add(text(""), true);
            section.add(text("zz"), true);

            List<RenderLayout.PositionedElement> elements = layout(section, 100, 60);
            assertEquals(3, elements.size());
            assertEquals(44, elements.get(0).backgroundX());
            assertEquals(50, elements.get(1).backgroundX());
            int shortRowX = switch (alignment) {
                case LEFT -> 44;
                case CENTER -> 47;
                case RIGHT -> 50;
            };
            assertEquals(shortRowX, elements.get(2).backgroundX());
            assertEquals(6, elements.get(2).backgroundWidth());
            assertEquals(8, elements.get(0).rowY() + elements.get(0).rowHeight());
        }
    }

    @Test void inlineSpacingUsesSectionScaleWithDifferentElementScalesAndOffsets() {
        RenderSection section = new RenderSection(new SectionLayout(
                Anchor.TOP_LEFT, 2, 2, RowAlignment.LEFT, InlineAlignment.BOTTOM,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 1.5f, 12, true, 2, 1));
        RenderElement.Texture icon = new RenderElement.Texture(null, 2f, 0, 3, 2);
        section.add(icon, false);
        section.add(text("yyyy"), true);
        ElementWidthProvider widths = (element, scale) -> element instanceof RenderElement.Texture texture
                ? RenderDimensions.scaleLength(RenderDimensions.BASE_ICON_SIZE, scale * texture.scale())
                : TEXT_WIDTH.width(element, scale);
        ElementHeightProvider heights = (element, scale) -> element instanceof RenderElement.Texture texture
                ? RenderDimensions.scaleLength(RenderDimensions.BASE_ICON_SIZE, scale * texture.scale())
                : TEXT_HEIGHT.height(element, scale);
        List<RenderLayout.PositionedElement> elements = engine.layout(
                List.of(section), widths, heights, 100, 60).elements();

        assertEquals(7, elements.get(0).backgroundX());
        assertEquals(10, elements.get(0).contentX());
        assertEquals(41, elements.get(0).backgroundWidth());
        assertEquals(43, elements.get(1).backgroundX());
        assertEquals(9, elements.get(1).backgroundWidth());
        assertEquals(32, elements.get(1).contentY());
        assertEquals(39, elements.get(0).rowHeight());
    }

    @Test void layoutRejectsNegativeInlineSpacing() {
        assertThrows(IllegalArgumentException.class, () -> new SectionLayout(
                Anchor.TOP_LEFT, 2, 2, RowAlignment.LEFT, InlineAlignment.CENTER,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 1f, 12, true, 2, -1));
    }

    private int shortRowX(RowAlignment alignment) {
        RenderSection section = section(Anchor.TOP_CENTER, alignment,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM);
        section.add(text("123456"), true);
        section.add(text("12"), true);
        return layout(section, 100, 60).get(1).backgroundX();
    }

    private int shortElementY(InlineAlignment alignment) {
        RenderSection section = new RenderSection(new SectionLayout(
                Anchor.TOP_LEFT, 2, 2, RowAlignment.LEFT, alignment,
                InlineDirection.LEFT_TO_RIGHT, RowDirection.TOP_TO_BOTTOM, 1f, 12, true, 2));
        section.add(text("12345678901234567890"), false);
        section.add(text("123456"), true);
        return layout(section, 100, 60).get(1).contentY();
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
                alignment, InlineAlignment.CENTER, inlineDirection, rowDirection, scale, rowHeight, false, 2));
    }

    private static RenderSection autoSection(Anchor anchor, RowDirection rowDirection) {
        return new RenderSection(new SectionLayout(anchor, anchor.defaultOffsetX(), 2,
                anchor.defaultRowAlignment(), InlineAlignment.CENTER, InlineDirection.LEFT_TO_RIGHT, rowDirection,
                1f, 12, true, 2));
    }

    private static RenderElement.Text text(String value) {
        return new RenderElement.Text(value, 0, 0, false, 0, 0);
    }
}
