package dev.ifair.luahud.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface NamespaceBuilder {
    NamespaceBuilder namespace(String name, Consumer<NamespaceBuilder> definition);

    <T> NamespaceBuilder value(String name, LuaType<T> type, LuaDoc doc, Supplier<? extends T> supplier);

    default NamespaceBuilder string(String name, String description, Supplier<String> supplier) {
        return value(name, LuaType.string(), LuaDoc.of(description), supplier);
    }

    default NamespaceBuilder integer(String name, String description, Supplier<Integer> supplier) {
        return value(name, LuaType.integer(), LuaDoc.of(description), supplier);
    }

    default NamespaceBuilder number(String name, String description, Supplier<Double> supplier) {
        return value(name, LuaType.number(), LuaDoc.of(description), supplier);
    }

    default NamespaceBuilder bool(String name, String description, Supplier<Boolean> supplier) {
        return value(name, LuaType.bool(), LuaDoc.of(description), supplier);
    }

    default <T> NamespaceBuilder model(String name, ModelBuilder<T> model, String description, Supplier<? extends T> supplier) {
        return value(name, model.type(), LuaDoc.of(description), supplier);
    }

    <R> FunctionBuilder<R> function(String name, LuaType<R> returnType, String description);
}
