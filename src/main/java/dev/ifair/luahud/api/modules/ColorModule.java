package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.api.models.ColorModel;
import dev.ifair.luahud.api.models.ColorModel.Color;

public final class ColorModule {
    private ColorModule() {}

    public static void register(LuaApiRegistrar api, ModelBuilder<Color> model) {
        api.root().namespace("color", color -> {
            var rgb = color.function("rgb", model.type(), "Create a mutable Color from byte channels; omitted alpha is 255.");
            var r = rgb.parameter("r", LuaType.integer(), "Red channel (0 to 255)");
            var g = rgb.parameter("g", LuaType.integer(), "Green channel (0 to 255)");
            var b = rgb.parameter("b", LuaType.integer(), "Blue channel (0 to 255)");
            var a = rgb.optionalParameter("a", LuaType.integer(), "Alpha channel (0 to 255); defaults to 255");
            rgb.call(args -> new Color(args.get(r), args.get(g), args.get(b), args.get(a) == null ? 255 : args.get(a)));

            var from = color.function("from", model.type(), "Create a Color from an ARGB integer or a 6-digit RGB / 8-digit ARGB hex string.");
            var value = from.parameter("value", LuaType.<Object>union(LuaType.integer(), LuaType.string()), "ARGB integer or RGB/ARGB hex string, optionally prefixed with #");
            from.call(args -> args.get(value) instanceof Number number
                    ? ColorModel.fromInt(number.intValue()) : ColorModel.fromString((String) args.get(value)));

            var fromRgb = color.function("fromRgb", model.type(), "Create an opaque Color from a packed RGB integer; upper bits are ignored.");
            var packed = fromRgb.parameter("value", LuaType.integer(), "Packed RGB integer (0xRRGGBB)");
            fromRgb.call(args -> ColorModel.fromRgb(args.get(packed)));
        });
    }
}
