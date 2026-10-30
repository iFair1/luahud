package dev.ifair.luahud.core;

/** Immutable profile metadata exposed to Lua. */
public record Profile(String name, boolean active, boolean hidden, String keybind) {}
