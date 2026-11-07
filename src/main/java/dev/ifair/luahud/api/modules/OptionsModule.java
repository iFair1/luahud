package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaType;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.SimpleOption;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Read-only client options. Minecraft's visitor drives reads, completions and typed overloads. */
public final class OptionsModule {
    private record Option(LuaType<?> type, Function<GameOptions, ?> read) {}

    private static final LuaType<Object> OPTION_VALUE = LuaType.union(
            LuaType.bool(), LuaType.integer(), LuaType.number(), LuaType.string());

    private OptionsModule() {}

    public static void register(LuaApiRegistrar api, Supplier<GameOptions> options) {
        GameOptions current = options.get();
        Map<String, Option> catalogue = current == null ? DefaultCatalogue.OPTIONS : createOptions(current);
        var getOption = api.root().function("getOption", OPTION_VALUE,
                "Read the current Minecraft client setting using its options.txt name. Unknown names raise an error. "
                        + "Numbers use the game's stored units (for example mouse sensitivity is 0–1); "
                        + "enum values are lowercase names with underscores. This does not change settings.");
        // Literal overloads provide both completions and precise return types; an alias
        // listing the same names would make language servers suggest each name twice.
        var name = getOption.parameter("name", LuaType.string(), "Client option name; available names depend on the Minecraft version");
        catalogue.forEach((key, option) -> getOption.overload(option.type(), LuaType.literal(key)));
        getOption.call(args -> {
            String key = args.get(name);
            Option option = catalogue.get(key);
            if (option == null) throw new IllegalArgumentException("Unknown Minecraft option: " + key);
            GameOptions value = options.get();
            if (value == null) throw new IllegalStateException("Minecraft client options are unavailable");
            return option.read().apply(value);
        });
    }

    private static Map<String, Option> createOptions(GameOptions sample) {
        Map<String, Option> options = new TreeMap<>();
        sample.accept(new ValueVisitor() {
            @Override void visit(String name, Object value) {
                LuaType<?> type = valueType(value);
                // Resource-pack lists and other compound settings are outside this scalar API.
                if (type != null) add(options, name, type, current -> read(current, name));
            }
        });
        addExtraOptions(options);
        return Collections.unmodifiableMap(options);
    }

    /** Add settings which Minecraft does not include in its persistence visitor here. */
    private static void addExtraOptions(Map<String, Option> options) {
        add(options, "smoothCameraEnabled", LuaType.bool(), value -> value.smoothCameraEnabled);
        add(options, "perspective", enumType(Perspective.class), value -> enumName(value.getPerspective()));
    }

    private static void add(Map<String, Option> options, String name, LuaType<?> type, Function<GameOptions, ?> read) {
        if (options.putIfAbsent(name, new Option(type, read)) != null) {
            throw new IllegalArgumentException("Duplicate Minecraft option: " + name);
        }
    }

    private static Object read(GameOptions options, String name) {
        var visitor = new ValueVisitor() {
            private Object result;

            @Override void visit(String key, Object value) {
                if (name.equals(key)) result = value instanceof Enum<?> e ? enumName(e) : value;
            }
        };
        // Do not retain SimpleOption instances: the supplied GameOptions may be replaced.
        options.accept(visitor);
        return visitor.result;
    }

    private static LuaType<?> valueType(Object value) {
        if (value instanceof Boolean) return LuaType.bool();
        if (value instanceof Integer) return LuaType.integer();
        if (value instanceof Float || value instanceof Double) return LuaType.number();
        if (value instanceof String) return LuaType.string();
        if (value instanceof Enum<?> e) return enumType(e.getDeclaringClass());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static LuaType<String> enumType(Class<?> type) {
        List<LuaType<String>> values = Arrays.stream(type.getEnumConstants())
                .map(value -> LuaType.literal(enumName((Enum<?>) value))).toList();
        return values.size() == 1 ? values.getFirst() : LuaType.union(values.toArray(LuaType[]::new));
    }

    private static String enumName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** Return every visited value unchanged so traversal cannot alter settings or key bindings. */
    private abstract static class ValueVisitor implements GameOptions.Visitor {
        abstract void visit(String name, Object value);

        @Override public <T> void accept(String name, SimpleOption<T> option) {
            visit(name, option.getValue());
        }

        @Override public int visitInt(String name, int value) {
            visit(name, value);
            return value;
        }

        @Override public boolean visitBoolean(String name, boolean value) {
            visit(name, value);
            return value;
        }

        @Override public String visitString(String name, String value) {
            visit(name, value);
            return value;
        }

        @Override public float visitFloat(String name, float value) {
            visit(name, value);
            return value;
        }

        @Override public <T> T visitObject(String name, T value, Function<String, T> parse, Function<T, String> serialize) {
            visit(name, value);
            return value;
        }
    }

    /** Metadata export needs no client, settings files, resource-pack tracking or window. */
    private static final class DefaultCatalogue {
        private static final Map<String, Option> OPTIONS = createOptions(new GameOptions(null, null) {
            @Override public void load() {}
        });
    }
}
