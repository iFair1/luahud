package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.error.ErrorEntry;

/** Registers the immutable error snapshot delivered to Lua error listeners. */
public final class ErrorModel {
    private ErrorModel() {}

    public static ModelBuilder<ErrorEntry> register(LuaApiRegistrar api) {
        ModelBuilder<ErrorEntry> model = api.model("Error", ErrorEntry.class);
        model.number("id", "Stable ID while this error remains active", value -> (double) value.id());
        model.string("group", "Profile name, or LuaHUD for system errors", ErrorEntry::group);
        model.bool("system", "Whether this is a LuaHUD system error", ErrorEntry::system);
        model.string("phase", "Originating event, load phase, timer, or system component", ErrorEntry::phase);
        model.integer("line", "Source line number, or -1 when unavailable", value -> {
            try {
                return Integer.parseInt(value.line());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        });
        model.string("message", "Error message without the source location prefix", ErrorEntry::displayMessage);
        model.integer("count", "Occurrence count at the time of this snapshot", ErrorEntry::count);
        model.bool("isNew", "Whether this snapshot represents the first occurrence of the active error", ErrorEntry::isNew);
        return model;
    }
}
