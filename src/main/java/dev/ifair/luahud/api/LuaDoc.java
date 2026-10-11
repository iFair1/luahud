package dev.ifair.luahud.api;

import java.util.Objects;

public record LuaDoc(String description) {
    public static final LuaDoc NONE = new LuaDoc("");

    public LuaDoc {
        description = Objects.requireNonNull(description, "description");
    }

    public static LuaDoc of(String description) {
        return new LuaDoc(description);
    }
}
