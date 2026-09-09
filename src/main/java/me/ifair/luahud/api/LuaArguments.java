package me.ifair.luahud.api;

public interface LuaArguments {
    <T> T get(LuaParameter<T> parameter);
}
