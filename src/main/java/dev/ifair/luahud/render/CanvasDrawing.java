package dev.ifair.luahud.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;

/** A bounded, locally positioned display list. Only the recording invocation can append commands. */
final class CanvasDrawing {
    final float width;
    final float height;
    final float scale;
    final boolean clip;
    final boolean visible;
    private final List<Command> commands = new ArrayList<>();
    private final List<Command> view = java.util.Collections.unmodifiableList(commands);

    CanvasDrawing(float width, float height, float scale, boolean clip, boolean visible) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.clip = clip;
        this.visible = visible;
    }

    sealed interface Command permits Content, Mesh, Image, Faded {}
    record Faded(Command command, float opacity) implements Command {}
    record Content(RenderElement element, CanvasTransform transform) implements Command {}
    record Mesh(List<CanvasGeometry.Quad> quads) implements Command {
        Mesh { quads = List.copyOf(quads); }
    }
    record Image(Identifier texture, int sourceWidth, int sourceHeight,
                 float width, float height, int sourceX, int sourceY, int regionWidth, int regionHeight,
                 int left, int right, int top, int bottom, boolean nineSlice,
                 CanvasTransform transform) implements Command {}

    void add(Command command) { commands.add(command); }
    List<Command> commands() { return view; }
}
