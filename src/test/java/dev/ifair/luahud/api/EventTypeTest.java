package dev.ifair.luahud.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTypeTest {
    @ParameterizedTest
    @ValueSource(strings = {"tick", "item-pickup", "addon-event-2", "_private", "onDamage2"})
    void eventNamesSupportBuiltInAndAddonConventions(String name) {
        assertDoesNotThrow(() -> new EventType<>(name, "fixture", LuaType.nil(), LuaDoc.NONE));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "two words", "1event", "-event", "event.name", "event\n", "event'", "event\""})
    void invalidNamesAreRejectedBeforeRegistration(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> new EventType<>(name, "fixture", LuaType.nil(), LuaDoc.NONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadContracts")
    void payloadValidationChecksNestedValuesAndNullability(String name, LuaType<?> type,
                                                          Object accepted, Object rejected) {
        EventType<?> event = new EventType<>("example", "fixture", type, LuaDoc.NONE);

        assertDoesNotThrow(() -> event.validatePayload(accepted));
        assertThrows(IllegalArgumentException.class, () -> event.validatePayload(rejected));
    }

    private static Stream<Arguments> payloadContracts() {
        ModelBuilder<String> model = new ModelBuilder<>("fixture", "Label", String.class);
        return Stream.of(
                Arguments.of("no payload", LuaType.nil(), null, "unexpected"),
                Arguments.of("integer rejects fractions", LuaType.integer(), 3, 3.5),
                Arguments.of("number rejects numeric strings", LuaType.number(), 3.5, "3.5"),
                Arguments.of("required string rejects nil", LuaType.string(), "value", null),
                Arguments.of("boolean rejects numbers", LuaType.bool(), false, 0),
                Arguments.of("optional string", LuaType.optional(LuaType.string()), null, 1),
                Arguments.of("literal", LuaType.literal("tick"), "tick", "render"),
                Arguments.of("model Java type", model.type(), "label", 42),
                Arguments.of("union accepts either branch", LuaType.union(LuaType.string(), LuaType.integer()), 7, false),
                Arguments.of("list elements", LuaType.list(LuaType.integer()), List.of(1, 2), List.of(1, "2")),
                Arguments.of("map values", LuaType.map(LuaType.bool()), Map.of("a", true), Map.of("a", "true")),
                Arguments.of("map keys", LuaType.map(LuaType.bool()), Map.of("a", true), Map.of(1, true)),
                Arguments.of("nested collections", LuaType.list(LuaType.map(LuaType.integer())),
                        List.of(Map.of("a", 1)), List.of(Map.of("a", "1"))));
    }
}
