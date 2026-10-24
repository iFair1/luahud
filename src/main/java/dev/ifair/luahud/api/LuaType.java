package dev.ifair.luahud.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LuaType<T> {
    public enum Kind { NIL, ANY, STRING, INTEGER, NUMBER, BOOLEAN, ALIAS, LITERAL, UNION, OPTIONAL, LIST, MAP, MODEL, MODEL_INPUT, INPUT_TABLE, FUNCTION }

    public record InputField(String name, LuaType<?> type, LuaDoc doc, boolean optional) {}

    public static final class InputTableBuilder {
        private final Map<String, InputField> fields = new LinkedHashMap<>();

        public InputTableBuilder field(String name, LuaType<?> type, String description) {
            return field(name, type, LuaDoc.of(description), false);
        }

        public InputTableBuilder optional(String name, LuaType<?> type, String description) {
            return field(name, type, LuaDoc.of(description), true);
        }

        public InputTableBuilder field(String name, LuaType<?> type, LuaDoc doc, boolean optional) {
            validateName(name);
            if (fields.putIfAbsent(name, new InputField(name, Objects.requireNonNull(type), Objects.requireNonNull(doc), optional)) != null) {
                throw new IllegalArgumentException("Duplicate input field: " + name);
            }
            return this;
        }
    }

    private final Supplier<? extends Collection<String>> stringValues;

    private final Kind kind;
    private final String displayName;
    private final List<LuaType<?>> alternatives;
    private final LuaType<?> elementType;
    private final LuaType<String> keyType;
    private final ModelBuilder<?> model;
    private final Map<String, InputField> inputFields;

    private LuaType(Kind kind, String displayName, List<LuaType<?>> alternatives, LuaType<?> elementType,
                    ModelBuilder<?> model, Map<String, InputField> inputFields) {
        this(kind, displayName, alternatives, elementType, model, inputFields, null);
    }

    private LuaType(Kind kind, String displayName, List<LuaType<?>> alternatives, LuaType<?> elementType,
                    ModelBuilder<?> model, Map<String, InputField> inputFields,
                    Supplier<? extends Collection<String>> stringValues) {
        this(kind, displayName, alternatives, elementType, model, inputFields, stringValues, null);
    }

    private LuaType(Kind kind, String displayName, List<LuaType<?>> alternatives, LuaType<?> elementType,
                    ModelBuilder<?> model, Map<String, InputField> inputFields,
                    Supplier<? extends Collection<String>> stringValues, LuaType<String> keyType) {
        this.keyType = keyType;
        this.stringValues = stringValues;
        this.kind = kind;
        this.displayName = displayName;
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        this.elementType = elementType;
        this.model = model;
        this.inputFields = inputFields == null ? Map.of() : Collections.unmodifiableMap(inputFields);
    }

    public static LuaType<Void> nil() { return simple(Kind.NIL, "nil"); }
    public static LuaType<Object> any() { return simple(Kind.ANY, "any"); }
    public static LuaType<String> string() { return simple(Kind.STRING, "string"); }
    public static LuaType<Integer> integer() { return simple(Kind.INTEGER, "integer"); }
    public static LuaType<Double> number() { return simple(Kind.NUMBER, "number"); }
    public static LuaType<Boolean> bool() { return simple(Kind.BOOLEAN, "boolean"); }

    /** Creates a named annotation type with the same runtime contract as its underlying type. */
    public static <T> LuaType<T> alias(String name, LuaType<T> underlyingType) {
        validateName(name);
        return new LuaType<>(Kind.ALIAS, name, null, Objects.requireNonNull(underlyingType), null, null);
    }

    /**
     * Creates a string alias whose literal values are read only when generating a stub.
     * The supplier should return the complete current set; values are sorted and deduplicated.
     * Runtime conversion remains string-based, so registry validation stays with the API consumer.
     */
    public static LuaType<String> stringAlias(String name, Supplier<? extends Collection<String>> values) {
        validateName(name);
        return new LuaType<>(Kind.ALIAS, name, null, string(), null, null, Objects.requireNonNull(values));
    }

    /** Returns a fresh, deterministic snapshot of the alias's documented string literals. */
    public List<String> stringValues() {
        return stringValues == null ? List.of() : stringValues.get().stream().distinct().sorted().toList();
    }

    public static LuaType<String> literal(String value) {
        return new LuaType<>(Kind.LITERAL, Objects.requireNonNull(value), null, null, null, null);
    }

    @SafeVarargs
    public static <T> LuaType<T> union(LuaType<? extends T>... types) {
        if (types.length < 2) throw new IllegalArgumentException("A union needs at least two alternatives");
        List<LuaType<?>> values = new ArrayList<>(types.length);
        Collections.addAll(values, types);
        return new LuaType<>(Kind.UNION, "union", values, null, null, null);
    }

    public static <T> LuaType<T> optional(LuaType<T> type) {
        return new LuaType<>(Kind.OPTIONAL, "optional", null, Objects.requireNonNull(type), null, null);
    }

    public static <T> LuaType<List<T>> list(LuaType<T> element) {
        return new LuaType<>(Kind.LIST, "list", null, Objects.requireNonNull(element), null, null);
    }

    public static <T> LuaType<Map<String, T>> map(LuaType<T> value) {
        return map(string(), value);
    }

    /** Creates a string-keyed map with a documented key type, such as a registry ID alias. */
    public static <T> LuaType<Map<String, T>> map(LuaType<String> key, LuaType<T> value) {
        LuaType<?> underlying = Objects.requireNonNull(key);
        while (underlying.kind() == Kind.ALIAS) underlying = underlying.elementType();
        if (underlying.kind() != Kind.STRING) throw new IllegalArgumentException("Map keys must use a string type or alias");
        return new LuaType<>(Kind.MAP, "map", null, Objects.requireNonNull(value), null, null, null, key);
    }

    public static <T> LuaType<T> model(ModelBuilder<T> model) {
        return new LuaType<>(Kind.MODEL, model.name(), null, null, Objects.requireNonNull(model), null);
    }

    public static <T> LuaType<T> modelInput(ModelBuilder<T> model) {
        return new LuaType<>(Kind.MODEL_INPUT, model.name() + "Input", null, null, Objects.requireNonNull(model), null);
    }

    /** Defines a strict options table which rejects undeclared keys. */
    public static LuaType<LuaTableValue> inputTable(String name, Consumer<InputTableBuilder> definition) {
        InputTableBuilder builder = new InputTableBuilder();
        definition.accept(builder);
        return new LuaType<>(Kind.INPUT_TABLE, Objects.requireNonNull(name), null, null, null, new LinkedHashMap<>(builder.fields));
    }

    /** Defines a strict recursive table; self can be used in nested fields and lists. */
    public static LuaType<LuaTableValue> inputTable(String name,
            BiConsumer<InputTableBuilder, LuaType<LuaTableValue>> definition) {
        Map<String, InputField> fields = new LinkedHashMap<>();
        LuaType<LuaTableValue> self = new LuaType<>(Kind.INPUT_TABLE, Objects.requireNonNull(name), null, null, null, fields);
        InputTableBuilder builder = new InputTableBuilder();
        definition.accept(builder, self);
        fields.putAll(builder.fields);
        return self;
    }

    /** Documentation-only callback type. Registrars reject it for addon-defined functions. */
    public static LuaType<Object> function() { return simple(Kind.FUNCTION, "function"); }

    private static <T> LuaType<T> simple(Kind kind, String name) {
        return new LuaType<>(kind, name, null, null, null, null);
    }

    public Kind kind() { return kind; }
    public String displayName() { return displayName; }
    public List<LuaType<?>> alternatives() { return alternatives; }
    public LuaType<String> keyType() { return keyType; }
    public LuaType<?> elementType() { return elementType; }
    public ModelBuilder<?> model() { return model; }
    public Map<String, InputField> inputFields() { return inputFields; }

    public String luaName() {
        return switch (kind) {
            case NIL, ANY, STRING, INTEGER, NUMBER, BOOLEAN, FUNCTION -> displayName;
            case LITERAL -> "\"" + displayName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case UNION -> String.join(" | ", alternatives.stream().map(LuaType::luaName).toList());
            case OPTIONAL -> elementType.luaName() + "?";
            case LIST -> elementType.luaName() + "[]";
            case MAP -> "table<" + keyType.luaName() + ", " + elementType.luaName() + ">";
            case ALIAS, MODEL, MODEL_INPUT, INPUT_TABLE -> displayName;
        };
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid Lua identifier: " + name);
        }
    }
}
