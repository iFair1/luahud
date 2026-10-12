package dev.ifair.luahud.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A registry-owned event definition. Retain the returned handle to publish typed payloads. */
public final class EventType<T> {
    private final String name;
    private final String providerId;
    private final LuaType<T> payloadType;
    private final LuaDoc doc;

    public EventType(String name, String providerId, LuaType<T> payloadType, LuaDoc doc) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid event name: " + name);
        }
        this.name = name;
        this.providerId = Objects.requireNonNull(providerId);
        this.payloadType = Objects.requireNonNull(payloadType);
        this.doc = Objects.requireNonNull(doc);
        if (payloadType.kind() == LuaType.Kind.FUNCTION || payloadType.kind() == LuaType.Kind.MODEL_INPUT) {
            throw new IllegalArgumentException("Event payload must be an output type");
        }
    }

    public String name() { return name; }
    public String providerId() { return providerId; }
    public LuaType<T> payloadType() { return payloadType; }
    public LuaDoc doc() { return doc; }
    public boolean hasPayload() { return payloadType.kind() != LuaType.Kind.NIL; }

    /** Checks string-based publications as well as typed calls made through raw Java types. */
    public void validatePayload(Object payload) {
        if (!accepts(payloadType, payload)) {
            throw new IllegalArgumentException("Invalid payload for event '" + name + "': expected "
                    + payloadType.luaName());
        }
    }

    private static boolean accepts(LuaType<?> type, Object value) {
        return switch (type.kind()) {
            case NIL -> value == null;
            case OPTIONAL -> value == null || accepts(type.elementType(), value);
            case UNION -> type.alternatives().stream().anyMatch(part -> accepts(part, value));
            case ANY -> value == null || value instanceof String || value instanceof Boolean
                    || value instanceof Number || value instanceof LuaTableValue
                    || value instanceof List<?> || value instanceof Map<?, ?>;
            case STRING -> value instanceof String;
            case LITERAL -> type.displayName().equals(value);
            case INTEGER -> value instanceof Integer;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case MODEL -> type.model().javaType().isInstance(value);
            case LIST -> value instanceof List<?> list
                    && list.stream().allMatch(item -> accepts(type.elementType(), item));
            case MAP -> value instanceof Map<?, ?> map && map.keySet().stream().allMatch(String.class::isInstance)
                    && map.values().stream().allMatch(item -> accepts(type.elementType(), item));
            case INPUT_TABLE -> value instanceof LuaTableValue;
            case MODEL_INPUT, FUNCTION -> false;
        };
    }
}
