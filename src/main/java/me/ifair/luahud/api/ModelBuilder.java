package me.ifair.luahud.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Defines a lazy Java model's output fields and its plain-table input contract. */
public final class ModelBuilder<T> {
    public enum InputMode { OUTPUT_ONLY, REQUIRED, OPTIONAL, DEFAULT }

    /** A typed field handle shared by accessors, input conversion, and documentation. */
    public static final class Field<T, V> {
        private final ModelBuilder<?> owner;
        private final String name;
        private final LuaType<V> type;
        private final LuaDoc doc;
        private final boolean optional;
        private final Function<? super T, ? extends V> accessor;
        private InputMode inputMode = InputMode.OUTPUT_ONLY;
        private V defaultValue;
        private boolean frozen;

        private Field(ModelBuilder<?> owner, String name, LuaType<V> type, LuaDoc doc, boolean optional,
                      Function<? super T, ? extends V> accessor) {
            this.owner = owner;
            this.name = name;
            this.type = type;
            this.doc = doc;
            this.optional = optional;
            this.accessor = accessor;
        }

        public Field<T, V> inputRequired() { return input(InputMode.REQUIRED, null); }
        public Field<T, V> inputOptional() { return input(InputMode.OPTIONAL, null); }
        public Field<T, V> inputDefault(V value) { return input(InputMode.DEFAULT, Objects.requireNonNull(value)); }
        public Field<T, V> outputOnly() { return input(InputMode.OUTPUT_ONLY, null); }

        private Field<T, V> input(InputMode mode, V value) {
            owner.ensureMutable();
            if (frozen) throw new IllegalStateException("Lua model field registration is closed");
            inputMode = mode;
            defaultValue = value;
            return this;
        }

        public String name() { return name; }
        public LuaType<V> type() { return type; }
        public LuaDoc doc() { return doc; }
        /** Whether the output field can be nil; independent of input presence. */
        public boolean optional() { return optional; }
        public Function<? super T, ? extends V> accessor() { return accessor; }
        public InputMode inputMode() { return inputMode; }
        public V defaultValue() { return defaultValue; }
    }

    /** Engine-neutral, typed input values. Source fields remain lazy until read. */
    public static final class Fields<T> {
        private final ModelBuilder<T> model;
        private final LuaTableValue values;
        private final Supplier<? extends T> source;

        private Fields(ModelBuilder<T> model, LuaTableValue values, Supplier<? extends T> source) {
            this.model = model;
            this.values = values;
            this.source = source;
        }

        /** Original Java object for a model proxy, or null for a plain Lua table. */
        public T source() { return source.get(); }

        @SuppressWarnings("unchecked")
        public <V> V get(Field<? super T, V> field) {
            check(field);
            V value = values.contains(field.name()) ? (V) values.get(field.name()) : null;
            if (value != null) return value;
            if (field.inputMode() == InputMode.REQUIRED) throw new IllegalArgumentException("Missing field '" + field.name() + "'");
            return field.defaultValue();
        }

        /** True for an explicitly supplied Lua value, rather than a lazy source value or default. */
        public boolean isExplicit(Field<? super T, ?> field) {
            check(field);
            return values.isExplicit(field.name());
        }

        private void check(Field<? super T, ?> field) {
            if (!model.fields.contains(field)) throw new IllegalArgumentException("Field does not belong to model " + model.name);
            if (field.inputMode() == InputMode.OUTPUT_ONLY) throw new IllegalArgumentException("Field is output-only: " + field.name());
        }
    }

    private final String providerId;
    private final String name;
    private final Class<T> javaType;
    private final List<Field<? super T, ?>> fields = new ArrayList<>();
    private Function<Fields<T>, ? extends T> decoder;
    private boolean frozen;

    public ModelBuilder(String providerId, String name, Class<T> javaType) {
        this.providerId = Objects.requireNonNull(providerId);
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid Lua identifier: " + name);
        this.name = name;
        this.javaType = Objects.requireNonNull(javaType);
    }

    public <V> Field<T, V> field(String name, LuaType<V> type, String description, Function<? super T, ? extends V> accessor) {
        return field(name, type, description, false, accessor);
    }

    public <V> Field<T, V> optionalField(String name, LuaType<V> type, String description,
                                        Function<? super T, ? extends V> accessor) {
        return field(name, type, description, true, accessor);
    }

    private <V> Field<T, V> field(String name, LuaType<V> type, String description, boolean optional,
                                Function<? super T, ? extends V> accessor) {
        ensureMutable();
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid Lua identifier: " + name);
        if (fields.stream().anyMatch(field -> field.name().equals(name))) throw new IllegalArgumentException("Duplicate model field: " + name);
        Field<T, V> field = new Field<>(this, name, Objects.requireNonNull(type), LuaDoc.of(description), optional, Objects.requireNonNull(accessor));
        fields.add(field);
        return field;
    }

    public Field<T, String> string(String name, String description, Function<? super T, String> accessor) { return field(name, LuaType.string(), description, accessor); }
    public Field<T, Integer> integer(String name, String description, Function<? super T, Integer> accessor) { return field(name, LuaType.integer(), description, accessor); }
    public Field<T, Double> number(String name, String description, Function<? super T, Double> accessor) { return field(name, LuaType.number(), description, accessor); }
    public Field<T, Boolean> bool(String name, String description, Function<? super T, Boolean> accessor) { return field(name, LuaType.bool(), description, accessor); }
    public Field<T, String> optionalString(String name, String description, Function<? super T, String> accessor) { return optionalField(name, LuaType.string(), description, accessor); }
    public Field<T, Integer> optionalInteger(String name, String description, Function<? super T, Integer> accessor) { return optionalField(name, LuaType.integer(), description, accessor); }
    public Field<T, Double> optionalNumber(String name, String description, Function<? super T, Double> accessor) { return optionalField(name, LuaType.number(), description, accessor); }
    public Field<T, Boolean> optionalBool(String name, String description, Function<? super T, Boolean> accessor) { return optionalField(name, LuaType.bool(), description, accessor); }

    /** Builds a Java value using only fields declared as input-capable. */
    public ModelBuilder<T> fromFields(Function<Fields<T>, ? extends T> decoder) {
        ensureMutable();
        this.decoder = Objects.requireNonNull(decoder);
        return this;
    }

    public ModelBuilder<T> include(ModelBuilder<? super T> parent) {
        ensureMutable();
        for (Field<? super T, ?> field : parent.fields()) {
            if (fields.stream().anyMatch(existing -> existing.name().equals(field.name()))) {
                throw new IllegalArgumentException("Duplicate inherited model field: " + field.name());
            }
            fields.add(field);
        }
        return this;
    }

    public LuaType<T> type() { return LuaType.model(this); }
    /** Function argument type documenting the plain-table input contract. */
    public LuaType<T> inputType() { return LuaType.modelInput(this); }
    public String providerId() { return providerId; }
    public String name() { return name; }
    public Class<T> javaType() { return javaType; }
    public List<Field<? super T, ?>> fields() { return Collections.unmodifiableList(fields); }
    public boolean canDecode() { return decoder != null; }
    public T decode(LuaTableValue value) { return decode(value, () -> null); }

    public T decode(LuaTableValue value, Supplier<? extends T> source) {
        if (decoder == null) throw new IllegalStateException("Model " + name + " does not accept plain tables");
        T decoded = decoder.apply(new Fields<>(this, Objects.requireNonNull(value), Objects.requireNonNull(source)));
        return javaType.cast(Objects.requireNonNull(decoded, "Model decoder returned null: " + name));
    }
    public void freeze() { fields.forEach(field -> field.frozen = true); frozen = true; }
    private void ensureMutable() { if (frozen) throw new IllegalStateException("Lua model registration is closed; the registry has already been frozen"); }
}
