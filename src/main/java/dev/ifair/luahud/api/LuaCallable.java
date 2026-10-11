package dev.ifair.luahud.api;

@FunctionalInterface
public interface LuaCallable<R> {
    R call(LuaArguments arguments) throws Exception;
}
