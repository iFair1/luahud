package dev.ifair.luahud.render;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanvasGeometryTest {
    @Test void rectangleAndBorderStayInsideTheirDeclaredBounds() {
        var quads = CanvasGeometry.rect(2, 3, 20, 10, 100, 0x80FFFFFF, 2, 0xFF000000);
        assertFalse(quads.isEmpty());
        for (var quad : quads) {
            for (var v : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                assertTrue(v.x() >= 2 && v.x() <= 22);
                assertTrue(v.y() >= 3 && v.y() <= 13);
            }
        }
    }

    @Test void diagonalLinesHaveRequestedThicknessAndFlatCaps() {
        var quad = CanvasGeometry.line(0, 0, 10, 10, 4, -1).getFirst();
        for (var v : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
            double along = (v.x() + v.y()) / Math.sqrt(2);
            assertTrue(Math.abs(along) < 0.00001 || Math.abs(along - Math.sqrt(200)) < 0.00001);
            assertEquals(2, Math.abs(v.x() - v.y()) / Math.sqrt(2), 0.00001);
        }
    }

    @Test void gradientKeepsArgbColorsAndDirection() {
        var quad = CanvasGeometry.gradient(0, 0, 10, 20, 0x00FF0000, 0xFF0000FF, true).getFirst();
        assertEquals(0x00FF0000, quad.a().color());
        assertEquals(0x00FF0000, quad.b().color());
        assertEquals(0xFF0000FF, quad.c().color());
        assertEquals(0xFF0000FF, quad.d().color());
    }

    @Test void arcsSupportBothDirectionsAndBoundTessellation() {
        for (float sweep : new float[]{90, -90, 360, -360}) {
            var quads = CanvasGeometry.arc(0, 0, 1000000, 0, sweep, 10, -1);
            assertTrue(quads.size() <= 512);
            for (var quad : quads) {
                double area = 0;
                var vertices = List.of(quad.a(), quad.b(), quad.c(), quad.d());
                for (int i = 0; i < 4; i++) {
                    var a = vertices.get(i); var b = vertices.get((i + 1) % 4);
                    area += (double) a.x() * b.y() - (double) b.x() * a.y();
                }
                assertTrue(area <= 0);
            }
        }
    }

    @Test void zeroSizedShapesDoNotProduceGeometry() {
        assertTrue(CanvasGeometry.rect(0, 0, 0, 10, 0, -1, 0, -1).isEmpty());
        assertTrue(CanvasGeometry.gradient(0, 0, 10, 0, -1, -1, false).isEmpty());
        assertTrue(CanvasGeometry.line(1, 1, 1, 1, 2, -1).isEmpty());
        assertTrue(CanvasGeometry.arc(0, 0, 5, 0, 0, 2, -1).isEmpty());
    }
}
