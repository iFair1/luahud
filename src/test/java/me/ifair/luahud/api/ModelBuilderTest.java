package me.ifair.luahud.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelBuilderTest {
    private record Data(String label, Integer count) {}

    @Test void defaultsAndLazyValuesAreReadThroughTypedHandles() {
        ModelBuilder<Data> model = new ModelBuilder<>("fixture", "Data", Data.class);
        var label = model.string("label", "", Data::label).inputRequired();
        var count = model.integer("count", "", Data::count).inputDefault(1);
        model.fromFields(fields -> new Data(fields.get(label), fields.get(count)));
        assertEquals(new Data("plain", 1), model.decode(new LuaTableValue(Map.of("label", "plain"))));
        AtomicInteger reads = new AtomicInteger();
        LuaTableValue lazy = new LuaTableValue(Map.of("label", "lazy"), Set.of("count"), key -> {
            reads.incrementAndGet();
            return 7;
        });
        assertEquals(0, reads.get());
        assertEquals(new Data("lazy", 7), model.decode(lazy));
        assertEquals(1, reads.get());
        LuaTableValue nilSource = new LuaTableValue(Map.of("label", "nil"), Set.of("count"), key -> null);
        assertEquals(new Data("nil", 1), model.decode(nilSource));
    }

    @Test void foreignAndOutputOnlyHandlesCannotBeReadByDecoder() {
        ModelBuilder<Data> model = new ModelBuilder<>("fixture", "Data", Data.class);
        ModelBuilder<Data> other = new ModelBuilder<>("fixture", "Other", Data.class);
        var foreign = other.string("label", "", Data::label).inputRequired();
        var output = model.string("label", "", Data::label);
        model.fromFields(fields -> new Data(fields.get(foreign), 0));
        assertThrows(IllegalArgumentException.class, () -> model.decode(new LuaTableValue(Map.of("label", "x"))));
        model.fromFields(fields -> new Data(fields.get(output), 0));
        assertThrows(IllegalArgumentException.class, () -> model.decode(new LuaTableValue(Map.of("label", "x"))));
    }

    @Test void outputNullabilityAndInputPresenceAreIndependent() {
        ModelBuilder<Data> model = new ModelBuilder<>("fixture", "Data", Data.class);
        var count = model.integer("count", "", Data::count).inputDefault(1);
        assertFalse(count.optional());
        assertEquals(ModelBuilder.InputMode.DEFAULT, count.inputMode());
        var label = model.optionalString("label", "", Data::label).inputRequired();
        assertTrue(label.optional());
        assertEquals(ModelBuilder.InputMode.REQUIRED, label.inputMode());
    }

    @Test void freezingChildAlsoFreezesIncludedHandles() {
        ModelBuilder<CharSequence> parent = new ModelBuilder<>("fixture", "Parent", CharSequence.class);
        var length = parent.integer("length", "", CharSequence::length);
        ModelBuilder<String> child = new ModelBuilder<>("fixture", "Child", String.class).include(parent);
        child.freeze();
        assertThrows(IllegalStateException.class, length::inputRequired);
    }
}
