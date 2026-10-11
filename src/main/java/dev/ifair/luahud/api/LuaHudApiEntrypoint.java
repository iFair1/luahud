package dev.ifair.luahud.api;

/** Fabric entrypoint for adding values and functions to LuaHUD's {@code lh} table. */
public interface LuaHudApiEntrypoint {
    void register(LuaApiRegistrar api);
}
