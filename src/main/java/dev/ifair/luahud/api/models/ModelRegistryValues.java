package dev.ifair.luahud.api.models;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static dev.ifair.luahud.api.models.ModelRegistryValues.*;

public final class ModelRegistryValues {
    static <T> T registryValue(Registry<T> registry, String id, String kind) {
        Identifier identifier = identifier(id, kind);
        //? if <1.21.2 {
        return registry.getOrEmpty(identifier)
        //?} else {
        /*return registry.getOptionalValue(identifier)
        *///?}
                .orElseThrow(() -> new IllegalArgumentException(capitalize(kind) + " not found: " + id));
    }

    static Identifier identifier(String id, String kind) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) throw new IllegalArgumentException("Invalid " + kind + " ID: " + id);
        return identifier;
    }

    static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
