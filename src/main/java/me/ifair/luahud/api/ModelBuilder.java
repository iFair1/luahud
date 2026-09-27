package me.ifair.luahud.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ModelBuilder<T> {
    public record Field<T, V>(
            String name,
            LuaType<V> type,
            LuaDoc doc,
            boolean optional,
            Function<? super T, ? extends V> accessor
    ) {}

    private final String providerId;
    private final String name;
    private final Class<T> javaType;
    private final List<Field<? super T, ?>> fields = new ArrayList<>();
    private Function<LuaTableValue, ? extends T> decoder;
    private boolean frozen;

    public ModelBuilder(String providerId, String name, Class<T> javaType) {
        this.providerId = Objects.requireNonNull(providerId);
        this.name = Objects.requireNonNull(name);
        this.javaType = Objects.requireNonNull(javaType);
    }

    public <V> ModelBuilder<T> field(String name, LuaType<V> type, String description, Function<? super T, ? extends V> accessor) {
        return field(name, type, description, false, accessor);
    }

    public <V> ModelBuilder<T> optionalField(String name, LuaType<V> type, String description,
                                              Function<? super T, ? extends V> accessor) {
        return field(name, type, description, true, accessor);
    }

    private <V> ModelBuilder<T> field(String name, LuaType<V> type, String description, boolean optional,
                                      Function<? super T, ? extends V> accessor) {
        ensureMutable();
        if (fields.stream().anyMatch(field -> field.name().equals(name))) throw new IllegalArgumentException("Duplicate model field: " + name);
        fields.add(new Field<>(name, type, LuaDoc.of(description), optional, accessor));
        return this;
    }

    public ModelBuilder<T> string(String name, String description, Function<? super T, String> accessor) { return field(name, LuaType.string(), description, accessor); }
    public ModelBuilder<T> integer(String name, String description, Function<? super T, Integer> accessor) { return field(name, LuaType.integer(), description, accessor); }
    public ModelBuilder<T> number(String name, String description, Function<? super T, Double> accessor) { return field(name, LuaType.number(), description, accessor); }
    public ModelBuilder<T> bool(String name, String description, Function<? super T, Boolean> accessor) { return field(name, LuaType.bool(), description, accessor); }
    public ModelBuilder<T> optionalString(String name, String description, Function<? super T, String> accessor) { return optionalField(name, LuaType.string(), description, accessor); }
    public ModelBuilder<T> optionalInteger(String name, String description, Function<? super T, Integer> accessor) { return optionalField(name, LuaType.integer(), description, accessor); }
    public ModelBuilder<T> optionalNumber(String name, String description, Function<? super T, Double> accessor) { return optionalField(name, LuaType.number(), description, accessor); }
    public ModelBuilder<T> optionalBool(String name, String description, Function<? super T, Boolean> accessor) { return optionalField(name, LuaType.bool(), description, accessor); }

    /** Defines how a plain Lua table is converted when this model is passed to a Java function. */
    public ModelBuilder<T> fromTable(Function<LuaTableValue, ? extends T> decoder) {
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
    public String providerId() { return providerId; }
    public String name() { return name; }
    public Class<T> javaType() { return javaType; }
    public List<Field<? super T, ?>> fields() { return Collections.unmodifiableList(fields); }
    public boolean canDecode() { return decoder != null; }
    public T decode(LuaTableValue value) {
        if (decoder == null) throw new IllegalStateException("Model " + name + " cannot be used as a function argument");
        return decoder.apply(value);
    }
    public void freeze() { frozen = true; }
    private void ensureMutable() { if (frozen) throw new IllegalStateException("Lua model registration is closed; the registry has already been frozen"); }
}
