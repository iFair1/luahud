package me.ifair.luahud.render;

import java.util.ArrayList;
import java.util.List;

final class RenderLayoutEngine {
    private record MeasuredElement(RenderElement element, int width) {}
    private record MeasuredRow(List<MeasuredElement> elements, int width) {}

    RenderLayout layout(
            List<RenderSection> sections,
            ElementWidthProvider widthProvider,
            int screenWidth,
            int screenHeight
    ) {
        List<RenderLayout.PositionedElement> positioned = new ArrayList<>();

        for (RenderSection section : sections) {
            List<MeasuredRow> rows = measure(section, widthProvider);
            int sectionWidth = rows.stream().mapToInt(MeasuredRow::width).max().orElse(0);
            int sectionHeight = rows.size() * RenderDimensions.ROW_HEIGHT;
            SectionLayout options = section.layout();
            int sectionX = options.anchor().resolveX(screenWidth, sectionWidth, options.offsetX());
            int sectionY = options.anchor().resolveY(screenHeight, sectionHeight, options.offsetY());

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                MeasuredRow row = rows.get(rowIndex);
                int rowX = alignRow(sectionX, sectionWidth, row.width(), options.rowAlignment());
                int rowY = positionRow(sectionY, rows.size(), rowIndex, options.rowDirection());
                positionElements(positioned, row, rowX, rowY, options);
            }
        }

        return new RenderLayout(positioned);
    }

    private static List<MeasuredRow> measure(RenderSection section, ElementWidthProvider widthProvider) {
        List<MeasuredRow> rows = new ArrayList<>(section.rows().size());
        for (RenderRow row : section.rows()) {
            List<MeasuredElement> elements = row.elements().stream()
                    .map(element -> new MeasuredElement(element, Math.max(0, widthProvider.width(element))))
                    .toList();
            int contentWidth = elements.stream().mapToInt(MeasuredElement::width).sum();
            int padding = contentWidth == 0 ? 0 : RenderDimensions.PADDING_LEFT + RenderDimensions.PADDING_RIGHT;
            rows.add(new MeasuredRow(elements, contentWidth + padding));
        }
        return rows;
    }

    private static int alignRow(int sectionX, int sectionWidth, int rowWidth, RowAlignment alignment) {
        return switch (alignment) {
            case LEFT -> sectionX;
            case CENTER -> sectionX + (sectionWidth - rowWidth) / 2;
            case RIGHT -> sectionX + sectionWidth - rowWidth;
        };
    }

    private static int positionRow(int sectionY, int rowCount, int rowIndex, RowDirection direction) {
        int visualIndex = direction == RowDirection.TOP_TO_BOTTOM ? rowIndex : rowCount - rowIndex - 1;
        return sectionY + visualIndex * RenderDimensions.ROW_HEIGHT;
    }

    private static void positionElements(
            List<RenderLayout.PositionedElement> output,
            MeasuredRow row,
            int rowX,
            int rowY,
            SectionLayout options
    ) {
        int firstVisible = firstVisible(row.elements());
        int lastVisible = lastVisible(row.elements());
        int cursor = options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? rowX : rowX + row.width();

        for (int index = 0; index < row.elements().size(); index++) {
            MeasuredElement measured = row.elements().get(index);
            if (measured.width() == 0) continue;

            int leftPadding = index == (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? firstVisible : lastVisible)
                    ? RenderDimensions.PADDING_LEFT : 0;
            int rightPadding = index == (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT ? lastVisible : firstVisible)
                    ? RenderDimensions.PADDING_RIGHT : 0;
            int backgroundWidth = leftPadding + measured.width() + rightPadding;

            if (options.inlineDirection() == InlineDirection.RIGHT_TO_LEFT) cursor -= backgroundWidth;
            RenderElement element = measured.element();
            int backgroundX = cursor + element.offsetX();
            output.add(new RenderLayout.PositionedElement(
                    element,
                    options.anchor(),
                    backgroundX,
                    rowY + element.offsetY(),
                    backgroundX + leftPadding,
                    backgroundWidth
            ));
            if (options.inlineDirection() == InlineDirection.LEFT_TO_RIGHT) cursor += backgroundWidth;
        }
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
