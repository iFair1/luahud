package dev.ifair.luahud.api;

public interface LuaApiRegistrar {
    NamespaceBuilder root();

    /** Returns LuaHUD's existing model types; their definitions cannot be modified. */
    BuiltinModels models();

    <T> ModelBuilder<T> model(String name, Class<T> javaType);

    /** Registers a callback event with no arguments. Names are shared across all providers. */
    default EventType<Void> event(String name, String description) {
        return event(name, LuaType.nil(), description);
    }

    /** Registers an event carrying one value, typically an immutable record exposed by a model. */
    <T> EventType<T> event(String name, LuaType<T> payloadType, String description);
}
