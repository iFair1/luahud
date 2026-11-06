package dev.ifair.luahud.api;

public interface FunctionBuilder<R> {
    <T> LuaParameter<T> parameter(String name, LuaType<T> type, String description);
    <T> LuaParameter<T> optionalParameter(String name, LuaType<T> type, String description);
    /** Documents a signature using the declared parameter names in order.
     * When present, overloads replace the general signature in generated Lua stubs.
     * Runtime argument validation and dispatch still use the main signature and callable. */
    FunctionBuilder<R> overload(LuaType<?> returnType, LuaType<?>... parameterTypes);
    FunctionBuilder<R> call(LuaCallable<R> callable);
}
