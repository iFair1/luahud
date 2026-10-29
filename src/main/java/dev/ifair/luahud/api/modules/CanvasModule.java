package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.*;
import dev.ifair.luahud.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canvas-only primitives and resource-backed image options. */
final class CanvasModule {
    private static final Map<Identifier, ImageSize> IMAGE_SIZES = new LinkedHashMap<>();
    private record ImageSize(int width, int height) {}
    private record TextDimensions(double width, double height) {}
    private CanvasModule() {}

    static float number(LuaTableValue options, String key, float fallback) {
        double value = options == null ? fallback : options.number(key, fallback);
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) {
            throw new IllegalArgumentException(key + " must be finite and between -1000000 and 1000000");
        }
        return (float) value;
    }

    static float length(LuaTableValue options, String key, float fallback) {
        float value = number(options, key, fallback);
        CanvasGeometry.length(value);
        return value;
    }

    static void register(LuaApiRegistrar api, LuaType<Object> color) {
        LuaType<LuaTableValue> rect = LuaType.inputTable("RectOptions", fields -> fields
                .optional("opacity", LuaType.number(), "Opacity from 0 to 1; multiplies canvas opacity; defaults to 1")
                .optional("color", color, "Fill color; defaults to white")
                .optional("radius", LuaType.number(), "Corner radius; defaults to zero")
                .optional("borderColor", color, "Border color; defaults to white")
                .optional("borderWidth", LuaType.number(), "Inset border width; defaults to zero"));
        LuaType<LuaTableValue> gradient = LuaType.inputTable("GradientOptions", fields -> fields
                .optional("opacity", LuaType.number(), "Opacity from 0 to 1; multiplies canvas opacity; defaults to 1")
                .field("fromColor", color, "Top or left color")
                .field("toColor", color, "Bottom or right color")
                .optional("direction", LuaType.union(LuaType.literal("horizontal"), LuaType.literal("vertical")),
                        "Gradient direction; defaults to vertical"));
        LuaType<LuaTableValue> stroke = LuaType.inputTable("StrokeOptions", fields -> fields
                .optional("opacity", LuaType.number(), "Opacity from 0 to 1; multiplies canvas opacity; defaults to 1")
                .optional("color", color, "Color; defaults to white")
                .optional("thickness", LuaType.number(), "Stroke width; circle defaults to filled, line and arc to 1"));
        registerShape(api, "drawRect", List.of("x", "y", "width", "height"), rect, false,
                (p, o) -> CanvasGeometry.rect(p[0], p[1], p[2], p[3], length(o, "radius", 0),
                        color(o, "color"), length(o, "borderWidth", 0), color(o, "borderColor")));
        registerShape(api, "drawGradient", List.of("x", "y", "width", "height"), gradient, true,
                (p, o) -> CanvasGeometry.gradient(p[0], p[1], p[2], p[3], color(o, "fromColor"),
                        color(o, "toColor"), "horizontal".equals(o.string("direction", "vertical"))));
        registerShape(api, "drawLine", List.of("x1", "y1", "x2", "y2"), stroke, false,
                (p, o) -> CanvasGeometry.line(p[0], p[1], p[2], p[3], length(o, "thickness", 1), color(o, "color")));
        registerShape(api, "drawCircle", List.of("cx", "cy", "radius"), stroke, false,
                (p, o) -> CanvasGeometry.arc(p[0], p[1], p[2], 0, 360, length(o, "thickness", p[2]), color(o, "color")));
        registerShape(api, "drawArc", List.of("cx", "cy", "radius", "startAngle", "sweepAngle"), stroke, false,
                (p, o) -> CanvasGeometry.arc(p[0], p[1], p[2], p[3], p[4], length(o, "thickness", 1), color(o, "color")));

        var measureOptions = LuaType.inputTable("MeasureTextOptions", fields -> fields
                .optional("scale", LuaType.number(), "Text scale; defaults to 1"));
        var dimensions = api.model("TextDimensions", TextDimensions.class);
        dimensions.field("width", LuaType.number(), "Text width in local GUI units", TextDimensions::width);
        dimensions.field("height", LuaType.number(), "Text height in local GUI units", TextDimensions::height);
        var measure = api.root().function("measureText", dimensions.type(),
                "Measure text without drawing. Returns width and height in local GUI units, excluding shadow.");
        var text = measure.parameter("text", LuaType.any(), "Value converted to text");
        var opts = measure.optionalParameter("options", measureOptions, "Measurement options");
        measure.call(args -> {
            var renderer = MinecraftClient.getInstance().textRenderer;
            float scale = length(args.get(opts), "scale", 1);
            if (scale == 0) throw new IllegalArgumentException("Scale must be greater than zero");
            return new TextDimensions(renderer.getWidth(String.valueOf(args.get(text))) * (double) scale,
                    renderer.fontHeight * (double) scale);
        });
    }

    private interface Shape { List<CanvasGeometry.Quad> create(float[] parameters, LuaTableValue options); }

    private static void registerShape(LuaApiRegistrar api, String name, List<String> names,
                                       LuaType<LuaTableValue> options, boolean requiredOptions, Shape shape) {
        var function = api.root().function(name, LuaType.nil(), "Draw in the selected canvas using local GUI coordinates.");
        var parameters = names.stream().map(n -> function.parameter(n, LuaType.number(), "Local " + n)).toList();
        var opts = requiredOptions ? function.parameter("options", options, "Shape options")
                : function.optionalParameter("options", options, "Shape options");
        function.call(args -> {
            if (!RenderBuffer.isCanvas()) throw new IllegalStateException("Select a canvas before drawing shapes");
            float[] values = new float[parameters.size()];
            for (int i = 0; i < values.length; i++) {
                double value = args.get(parameters.get(i));
                if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) {
                    throw new IllegalArgumentException(names.get(i) + " must be finite and between -1000000 and 1000000");
                }
                values[i] = (float) value;
            }
            RenderBuffer.opacity(RenderModule.currentSectionState().opacity() * RenderModule.opacity(args.get(opts)));
            RenderBuffer.addMesh(shape.create(values, args.get(opts)));
            return null;
        });
    }

    private static int color(LuaTableValue options, String key) {
        return options != null && options.contains(key) ? RenderModule.color(options.get(key)) : 0xFFFFFFFF;
    }

    static void drawImage(Identifier texture, LuaTableValue options, CanvasTransform transform) {
        ImageSize size = IMAGE_SIZES.computeIfAbsent(texture, CanvasModule::readSize);
        drawImage(texture, size.width, size.height, options, transform);
    }

    static void drawImage(Identifier texture, int sourceWidth, int sourceHeight,
                          LuaTableValue options, CanvasTransform transform) {
        ImageSize size = new ImageSize(sourceWidth, sourceHeight);
        LuaTableValue region = options == null ? null : options.table("region");
        LuaTableValue slice = options == null ? null : options.table("nineSlice");
        if (region != null && slice != null) throw new IllegalArgumentException("region and nineSlice cannot be combined");
        int sx = region == null ? 0 : region.integer("x", 0);
        int sy = region == null ? 0 : region.integer("y", 0);
        int sw = region == null ? size.width : region.integer("width", size.width);
        int sh = region == null ? size.height : region.integer("height", size.height);
        if (sx < 0 || sy < 0 || sw <= 0 || sh <= 0 || (long) sx + sw > size.width || (long) sy + sh > size.height) {
            throw new IllegalArgumentException("Texture region must be inside the source image");
        }
        int left = slice == null ? 0 : slice.integer("left", 0);
        int right = slice == null ? 0 : slice.integer("right", 0);
        int top = slice == null ? 0 : slice.integer("top", 0);
        int bottom = slice == null ? 0 : slice.integer("bottom", 0);
        if (left < 0 || right < 0 || top < 0 || bottom < 0
                || (long) left + right >= size.width || (long) top + bottom >= size.height) {
            throw new IllegalArgumentException("Nine-slice borders must leave a nonempty source center");
        }
        RenderBuffer.addImage(texture, size.width, size.height, length(options, "width", sw), length(options, "height", sh),
                sx, sy, sw, sh, left, right, top, bottom, slice != null, transform);
    }

    static void clearImageSizes() { IMAGE_SIZES.clear(); }

    private static ImageSize readSize(Identifier texture) {
        try (var stream = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(texture).getInputStream();
             NativeImage image = NativeImage.read(stream)) {
            return new ImageSize(image.getWidth(), image.getHeight());
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot read texture: " + texture, error);
        }
    }
}
