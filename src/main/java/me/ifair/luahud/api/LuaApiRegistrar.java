package me.ifair.luahud.api;

public interface LuaApiRegistrar {
    NamespaceBuilder root();

    <T> ModelBuilder<T> model(String name, Class<T> javaType);
}
