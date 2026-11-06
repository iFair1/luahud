package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;

import java.util.Locale;

/** A color snapshot with byte channels and unambiguous ARGB conversions. */
public final class ColorModel {
    private ColorModel() {}

    public record Color(int r, int g, int b, int a) {
        public Color {
            checkChannel("r", r);
            checkChannel("g", g);
            checkChannel("b", b);
            checkChannel("a", a);
        }

        public int asInt() { return a << 24 | r << 16 | g << 8 | b; }
        public String asString() { return String.format(Locale.ROOT, "#%08X", asInt()); }
    }

    public static ModelBuilder<Color> register(LuaApiRegistrar api) {
        var model = api.model("Color", Color.class);
        var r = model.integer("r", "Red channel (0 to 255)", Color::r).inputRequired();
        var g = model.integer("g", "Green channel (0 to 255)", Color::g).inputRequired();
        var b = model.integer("b", "Blue channel (0 to 255)", Color::b).inputRequired();
        var a = model.integer("a", "Alpha channel (0 to 255); defaults to 255 for input tables", Color::a).inputDefault(255);
        model.integer("asInt", "Current channels packed as a signed ARGB integer (0xAARRGGBB)", Color::asInt);
        model.string("asString", "Current channels as #AARRGGBB", Color::asString);
        return model.fromFields(fields -> new Color(fields.get(r), fields.get(g), fields.get(b), fields.get(a)))
                .mutableTable();
    }

    public static Color fromInt(int argb) {
        return new Color(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24);
    }

    public static Color fromRgb(int rgb) { return fromInt(rgb | 0xFF000000); }

    public static Color fromString(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (!hex.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
            throw new IllegalArgumentException("Expected RGB (#RRGGBB) or ARGB (#AARRGGBB) hex color");
        }
        int packed = (int) Long.parseLong(hex, 16);
        return hex.length() == 6 ? fromRgb(packed) : fromInt(packed);
    }

    private static void checkChannel(String name, int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("Color channel '" + name + "' must be between 0 and 255");
    }
}
