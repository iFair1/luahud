package me.ifair.luahud.render;

import java.util.ArrayList;
import java.util.List;

final class RenderLayoutEngine {
    private record MeasuredElement(RenderElement element, int width, int height) {}
    private record MeasuredRow(List<MeasuredElement> elements, int width, int height) {}

    RenderLayout layout(
            List<RenderSection> sections,
            ElementWidthProvider widthProvider,
            ElementHeightProvider heightProvider,
            int screenWidth,
            int screenHeight
    ) {
        List<RenderLayout.PositionedElement> positioned = new ArrayList<>();

        for (RenderSection section : sections) {
            SectionLayout options = section.layout();
            List<MeasuredRow> rows = measure(section, widthProvider, heightProvider, options);
            int sectionWidth = rows.stream().mapToInt(MeasuredRow::width).max().orElse(0);
            int sectionHeight = rows.stream().mapToInt(row -> occupiedHeight(row, options)).sum();
            int sectionX = options.anchor().resolveX(screenWidth, sectionWidth, options.offsetX());
            int sectionY = options.anchor().resolveY(screenHeight, sectionHeight, options.offsetY());
            int rowCursor = options.rowDirection() == RowDirection.TOP_TO_BOTTOM
                    ? sectionY : sectionY + sectionHeight;

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                MeasuredRow row = rows.get(rowIndex);
                int rowX = alignRow(sectionX, sectionWidth, row.width(), options.rowAlignment());
                int rowY;
                if (options.rowDirection() == RowDirection.TOP_TO_BOTTOM) {
                    rowY = rowCursor;
                    rowCursor += occupiedHeight(row, options);
                } else {
                    rowCursor -= occupiedHeight(row, options);
                    rowY = rowCursor;
                }
                positionElements(positioned, row, rowX, rowY, row.height(), options);
            }
        }

        return new RenderLayout(positioned);
    }

    private static List<MeasuredRow> measure(
            RenderSection section,
            ElementWidthProvider widthProvider,
            ElementHeightProvider heightProvider,
            SectionLayout options
    ) {
        List<MeasuredRow> rows = new ArrayList<>(section.rows().size());
        for (RenderRow row : section.rows()) {
            List<MeasuredElement> elements = row.elements().stream()
                    .map(element -> new MeasuredElement(
                            element,
                            Math.max(0, widthProvider.width(element, options.scale())),
                            Math.max(0, heightProvider.height(element, options.scale()))))
                    .toList();
            int contentWidth = elements.stream().mapToInt(MeasuredElement::width).sum();
            int padding = contentWidth == 0 ? 0
                    : RenderDimensions.scaleOffset(RenderDimensions.PADDING_LEFT, options.scale())
                    + RenderDimensions.scaleOffset(RenderDimensions.PADDING_RIGHT, options.scale());
            int rowHeight = options.autoRowHeight() && !elements.isEmpty()
                    ? elements.stream().mapToInt(MeasuredElement::height).max().orElse(0)
                    : options.scaledRowHeight();
            rows.add(new MeasuredRow(elements, contentWidth + padding, rowHeight));
        }
        return rows;
    }

    private static int occupiedHeight(MeasuredRow row, SectionLayout options) {
        return row.height() + options.scaledLineSpacing();
    }

    private static int alignRow(int sectionX, int sectionWidth, int rowWidth, RowAlignment alignment) {
        return switch (alignment) {
            case LEFT -> sectionX;
            case CENTER -> sectionX + (sectionWidth - rowWidth) / 2;
            case RIGHT -> sectionX + sectionWidth - rowWidth;
        };
    }

    private static void positionElements(
            List<RenderLayout.PositionedElement> output,
            MeasuredRow row,
            int rowX,
            int rowY,
            int rowHeight,
            SectionLayout options
    ) {
        int firstVisible = firstVisible(row.elements());
        int lastVisible = lastVisible(row.elements());
        int cursor = options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? rowX : rowX + row.width();

        for (int index = 0; index < row.elements().size(); index++) {
            MeasuredElement measured = row.elements().get(index);
            if (measured.width() == 0) continue;

            int leftPadding = index == (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? firstVisible : lastVisible)
                    ? RenderDimensions.scaleOffset(RenderDimensions.PADDING_LEFT, options.scale()) : 0;
            int rightPadding = index == (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? lastVisible : firstVisible)
                    ? RenderDimensions.scaleOffset(RenderDimensions.PADDING_RIGHT, options.scale()) : 0;
            int backgroundWidth = leftPadding + measured.width() + rightPadding;

            if (options.inlineDirection() == InlineDirection.RIGHT_TO_LEFT) cursor -= backgroundWidth;
            RenderElement element = measured.element();
            int backgroundX = cursor + RenderDimensions.scaleOffset(element.offsetX(), options.scale());
            int elementRowY = rowY + RenderDimensions.scaleOffset(element.offsetY(), options.scale());
            int contentY = alignInline(elementRowY, rowHeight, measured.height(), options.inlineAlignment());
            output.add(new RenderLayout.PositionedElement(
                    element,
                    backgroundX,
                    elementRowY - options.spacingAbove(),
                    elementRowY,
                    contentY,
                    backgroundX + leftPadding,
                    backgroundWidth,
                    options.scale(),
                    rowHeight + options.scaledLineSpacing()
            ));
            if (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT) cursor += backgroundWidth;
        }
    }

    private static int alignInline(int rowY, int rowHeight, int elementHeight, InlineAlignment alignment) {
        return switch (alignment) {
            case TOP -> rowY;
            case CENTER -> rowY + (rowHeight - elementHeight) / 2;
            case BOTTOM -> rowY + rowHeight - elementHeight;
        };
    }

    private static int firstVisible(List<MeasuredElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).width() > 0) return i;
        }
        return -1;
    }

    private static int lastVisible(List<MeasuredElement> elements) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).width() > 0) return i;
        }
        return -1;
    }
}
