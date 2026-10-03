package me.ifair.luahud.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Engine-neutral representation of a Lua input table. */
public final class LuaTableValue {
    private final Map<String, Object> values;
    private final Set<String> lazyKeys;
    private final Function<String, Object> resolver;

    public LuaTableValue(Map<String, Object> values) {
        this(values, Set.of(), ignored -> null);
    }

    public LuaTableValue(Map<String, Object> values, Set<String> lazyKeys, Function<String, Object> resolver) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.lazyKeys = Collections.unmodifiableSet(new LinkedHashSet<>(lazyKeys));
        this.resolver = resolver;
    }

    public boolean isExplicit(String key) { return values.containsKey(key); }
    public boolean contains(String key) { return values.containsKey(key) || lazyKeys.contains(key); }
    public Object get(String key) { return values.containsKey(key) ? values.get(key) : lazyKeys.contains(key) ? resolver.apply(key) : null; }
    public String string(String key, String fallback) { return value(key, String.class, fallback); }
    public int integer(String key, int fallback) { Number value = number(key); return value == null ? fallback : value.intValue(); }
    public double number(String key, double fallback) { Number value = number(key); return value == null ? fallback : value.doubleValue(); }
    public boolean bool(String key, boolean fallback) { return value(key, Boolean.class, fallback); }
    public LuaTableValue table(String key) { return value(key, LuaTableValue.class, null); }
    public Map<String, Object> values() {
        if (lazyKeys.isEmpty()) return values;
        Map<String, Object> resolved = new LinkedHashMap<>(values);
        for (String key : lazyKeys) resolved.putIfAbsent(key, resolver.apply(key));
        return Collections.unmodifiableMap(resolved);
    }

    private Number number(String key) {
        Object value = get(key);
        return value instanceof Number number ? number : null;
    }

    private <T> T value(String key, Class<T> type, T fallback) {
        Object value = get(key);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }
}
