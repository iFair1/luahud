package me.ifair.luahud.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Engine-neutral representation of a Lua input table. */
public final class LuaTableValue {
    private final Map<String, Object> values;

    public LuaTableValue(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean contains(String key) { return values.containsKey(key); }
    public Object get(String key) { return values.get(key); }
    public String string(String key, String fallback) { return value(key, String.class, fallback); }
    public int integer(String key, int fallback) { return number(key) == null ? fallback : number(key).intValue(); }
    public double number(String key, double fallback) { return number(key) == null ? fallback : number(key).doubleValue(); }
    public boolean bool(String key, boolean fallback) { return value(key, Boolean.class, fallback); }
    public LuaTableValue table(String key) { return value(key, LuaTableValue.class, null); }
    public Map<String, Object> values() { return values; }

    private Number number(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number : null;
    }

    private <T> T value(String key, Class<T> type, T fallback) {
        Object value = values.get(key);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }
}
