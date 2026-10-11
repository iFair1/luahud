package dev.ifair.luahud.api;

public interface FunctionBuilder<R> {
    <T> LuaParameter<T> parameter(String name, LuaType<T> type, String description);
    <T> LuaParameter<T> optionalParameter(String name, LuaType<T> type, String description);
    FunctionBuilder<R> call(LuaCallable<R> callable);
}
