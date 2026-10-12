package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.EventType;
import java.util.concurrent.atomic.AtomicReference;
import dev.ifair.luahud.api.ModelBuilder;
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
    @Test void eventsMergeButConflictingAndRolledBackHandlesCannotPublish() {
        List<String> warnings = new ArrayList<>();
        ApiRegistry registry = new ApiRegistry(warnings::add);
        var first = registry.registrar("core").event("damage", LuaType.number(), "Damage");
        AtomicReference<EventType<String>> duplicate = new AtomicReference<>();
        registry.registerAddon("addon", api -> {
            duplicate.set(api.event("damage", LuaType.string(), "Conflict"));
            api.event("addonEvent", "");
        });
        AtomicReference<EventType<Void>> rolledBack = new AtomicReference<>();
        assertThrows(IllegalStateException.class, () -> registry.registerAddon("broken", api -> {
            rolledBack.set(api.event("partial", ""));
            throw new IllegalStateException("failed contribution");
        }));
        registry.freeze();
        assertSame(first, registry.event("damage"));
        registry.validateEvent(first, 2.5);
        assertEquals(2, registry.events().size());
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(duplicate.get(), "wrong"));
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(rolledBack.get(), null));
        assertTrue(warnings.getFirst().contains("addon"));
        assertTrue(warnings.getFirst().contains("damage"));
    }

    @Test void lateEventRegistrationInvalidNamesAndWrongPayloadsAreRejected() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("fixture");
        var event = api.event("count", LuaType.integer(), "");
        var empty = api.event("empty", "");
        assertThrows(IllegalArgumentException.class, () -> api.event("", ""));
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> api.event("late", ""));
        assertThrows(IllegalArgumentException.class, () -> registry.event("missing"));
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(event, "wrong"));
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(event, null));
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(empty, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.validateEvent(
                new EventType<>("count", "fixture", LuaType.integer(), dev.ifair.luahud.api.LuaDoc.NONE), 1));
    }
}
