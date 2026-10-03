package me.ifair.luahud.api.core;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.ModelBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiRegistryTest {
    @Test void namespacesMergeAndFirstLeafWins() {
        List<String> warnings = new ArrayList<>();
        ApiRegistry registry = new ApiRegistry(warnings::add);
        registry.registrar("core").root().namespace("example", table -> table.integer("one", "", () -> 1));
        registry.registerAddon("addon", api -> api.root().namespace("example", table -> table
                .integer("two", "", () -> 2).integer("one", "", () -> 99)));
        ApiRegistry.NamespaceNode example = (ApiRegistry.NamespaceNode) registry.rootNode().children().get("example");
        assertEquals(List.of("one", "two"), new ArrayList<>(example.children().keySet()));
        assertEquals("core", example.children().get("one").providerId());
        assertTrue(warnings.getFirst().contains("lh.example.one"));
    }

    @Test void tableLeafConflictDoesNotAbortOtherEntries() {
        List<String> warnings = new ArrayList<>();
        ApiRegistry registry = new ApiRegistry(warnings::add);
        registry.registrar("core").root().integer("taken", "", () -> 1);
        registry.registerAddon("addon", api -> api.root()
                .namespace("taken", table -> table.integer("lost", "", () -> 0))
                .integer("kept", "", () -> 2));
        assertFalse(registry.rootNode().children().containsKey("lost"));
        assertTrue(registry.rootNode().children().containsKey("kept"));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("lh.taken")));
    }

    @Test void throwingAddonIsRolledBack() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        assertThrows(IllegalStateException.class, () -> registry.registerAddon("broken", api -> {
            api.root().integer("partial", "", () -> 1);
            throw new IllegalStateException("boom");
        }));
        assertFalse(registry.rootNode().children().containsKey("partial"));
    }

    @Test void freezeRejectsLateNamespaceAndModelMutation() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("core");
        ModelBuilder<String> model = api.model("Value", String.class);
        var field = model.string("text", "", value -> value);
        var root = api.root();
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> root.integer("late", "", () -> 1));
        assertThrows(IllegalStateException.class, () -> model.string("late", "", value -> value));
        assertThrows(IllegalStateException.class, field::inputRequired);
    }

    @Test void modelsComposeInheritedFields() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        LuaApiRegistrar api = registry.registrar("core");
        ModelBuilder<CharSequence> base = api.model("Base", CharSequence.class);
        base.integer("length", "", CharSequence::length);
        ModelBuilder<String> child = api.model("Child", String.class).include(base);
        child.string("value", "", value -> value);
        assertEquals(List.of("length", "value"), child.fields().stream().map(ModelBuilder.Field::name).toList());
    }
}
