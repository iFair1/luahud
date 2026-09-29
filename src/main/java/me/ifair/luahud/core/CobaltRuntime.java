package me.ifair.luahud.core;

import me.ifair.luahud.api.*;
import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.error.ErrorManager;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** All Cobalt-specific binding and callback state lives behind this package-private adapter. */
public final class CobaltRuntime {
    private record Callback(LuaFunction function, String profile) {}
    private record Timer(int interval, Callback callback, int[] elapsed) {}

    private final ApiRegistry registry;
    private final Runnable invocationBoundary;
    private final Map<String, List<Callback>> listeners = new HashMap<>();
    private final List<Timer> timers = new ArrayList<>();
    // Keep model metadata owned by each Lua table so unreachable tables remain collectible.
    private final LuaValue modelValueKey = new LuaUserdata(new Object());
    private final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    private LuaState state;
    private String currentProfile;

    private record ModelValue(Supplier<Object> supplier, ModelBuilder<Object> model) {}

    public CobaltRuntime(ApiRegistry registry) {
        this(registry, () -> {});
    }

    public CobaltRuntime(ApiRegistry registry, Runnable invocationBoundary) {
        if (!registry.isFrozen()) throw new IllegalArgumentException("Lua API registry must be frozen before starting Cobalt");
        this.registry = registry;
        this.invocationBoundary = Objects.requireNonNull(invocationBoundary, "invocationBoundary");
    }

    public void start() throws LuaError {
        listeners.clear(); timers.clear();
        state = new LuaState();
        CoreLibraries.standardGlobals(state);
        state.globals().rawset("lh", namespace(registry.rootNode(), "lh"));
    }

    public boolean isReady() { return state != null; }

    public Varargs execute(String source, String name) throws LuaError, CompileException {
        return execute(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), name, name);
    }

    public Varargs execute(InputStream source, String name) throws LuaError, CompileException {
        return execute(source, name, name);
    }

    Varargs execute(InputStream source, String name, String profile) throws LuaError, CompileException {
        ready();
        String previousProfile = currentProfile;
        currentProfile = profile;
        try {
            invocationBoundary.run();
            return LuaThread.runMain(state, LoadState.load(state, source, name, state.globals()));
        } finally {
            currentProfile = previousProfile;
        }
    }

    public LuaValue global(String name) { ready(); return state.globals().rawget(name); }

    public void fireEvent(String eventName) {
        List<Callback> callbacks = listeners.get(eventName);
        if (callbacks == null) return;
        for (Callback callback : List.copyOf(callbacks)) invokeCallback(callback, eventName);
    }

    public void tickTimers() {
        for (Timer timer : List.copyOf(timers)) {
            if (++timer.elapsed[0] >= timer.interval) {
                timer.elapsed[0] = 0;
                invokeCallback(timer.callback, null);
            }
        }
    }

    private void invokeCallback(Callback callback, String cooldownKey) {
        String previousProfile = currentProfile;
        currentProfile = callback.profile;
        try {
            invocationBoundary.run();
            LuaThread.run(new LuaThread(state, callback.function), Constants.NONE);
        } catch (LuaError error) {
            ErrorManager.reportProfile(callback.profile, cooldownKey == null ? "timer" : cooldownKey, error);
        } finally {
            currentProfile = previousProfile;
        }
    }

    private LuaTable namespace(ApiRegistry.NamespaceNode definition, String path) throws LuaError {
        LuaTable table = new LuaTable();
        Map<String, ApiRegistry.ValueNode> lazy = new HashMap<>();
        for (ApiRegistry.Node child : definition.children().values()) {
            String childPath = path + "." + child.name();
            if (child instanceof ApiRegistry.NamespaceNode namespace) table.rawset(child.name(), namespace(namespace, childPath));
            else if (child instanceof ApiRegistry.ValueNode value) lazy.put(child.name(), value);
            else if (child instanceof ApiRegistry.FunctionNode function) table.rawset(child.name(), function(function, childPath));
        }
        if (!lazy.isEmpty()) {
            LuaTable metatable = new LuaTable();
            metatable.rawset("__index", LibFunction.create((luaState, ignored, key) -> {
                ApiRegistry.ValueNode value = key.isString() ? lazy.get(key.checkString()) : null;
                if (value == null) return Constants.NIL;
                return supplied(value.supplier(), value.type(), path + "." + value.name(), value.providerId());
            }));
            table.setMetatable(state, metatable);
        }
        return table;
    }

    private LuaFunction function(ApiRegistry.FunctionNode definition, String path) {
        return LibFunction.createV((luaState, args) -> {
            try {
                if (definition.special() == ApiRegistry.SpecialFunction.EVENT) {
                    String event = args.arg(1).checkString();
                    listeners.computeIfAbsent(event, ignored -> new ArrayList<>())
                            .add(new Callback(args.arg(2).checkFunction(), currentProfile));
                    return Constants.NIL;
                }
                if (definition.special() == ApiRegistry.SpecialFunction.INTERVAL) {
                    int interval = args.arg(1).checkInteger();
                    String unit = args.arg(2).checkString();
                    if (!unit.equals("ticks")) throw new LuaError("bad argument #2 (expected 'ticks')");
                    timers.add(new Timer(interval, new Callback(args.arg(3).checkFunction(), currentProfile), new int[]{0}));
                    return Constants.NIL;
                }

                List<Object> converted = new ArrayList<>(definition.parameters().size());
                for (int i = 0; i < definition.parameters().size(); i++) {
                    LuaParameter<?> parameter = definition.parameters().get(i);
                    try { converted.add(fromLua(args.arg(i + 1), parameter.type())); }
                    catch (LuaError error) { throw new LuaError("bad argument #" + (i + 1) + " ('" + parameter.name() + "'): " + error.getMessage()); }
                }
                LuaArguments arguments = new Arguments(definition, converted);
                Object result = definition.callable().call(arguments);
                return toLua(result, definition.returnType(), path, definition.providerId());
            } catch (LuaError error) {
                throw error;
            } catch (Throwable error) {
                throw providerError(path, definition.providerId(), error);
            }
        });
    }

    private LuaValue supplied(Supplier<?> supplier, LuaType<?> type, String path, String provider) throws LuaError {
        try {
            if (type.kind() == LuaType.Kind.MODEL) {
                @SuppressWarnings("unchecked") Supplier<Object> dynamic = (Supplier<Object>) supplier;
                @SuppressWarnings("unchecked") ModelBuilder<Object> model = (ModelBuilder<Object>) type.model();
                return modelTable(dynamic, model, path, provider);
            }
            return toLua(supplier.get(), type, path, provider);
        }
        catch (LuaError error) { throw error; }
        catch (Throwable error) { throw providerError(path, provider, error); }
    }

    private LuaError providerError(String path, String provider, Throwable error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new LuaError("Lua API provider '" + provider + "' failed at " + path + ": " + detail);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LuaValue toLua(Object value, LuaType<?> type, String path, String provider) throws LuaError {
        if (value == null || type.kind() == LuaType.Kind.NIL) return Constants.NIL;
        return switch (type.kind()) {
            case STRING, LITERAL -> luaString((String) value);
            case INTEGER -> ValueFactory.valueOf(((Number) value).intValue());
            case NUMBER -> ValueFactory.valueOf(((Number) value).doubleValue());
            case BOOLEAN -> ValueFactory.valueOf((Boolean) value);
            case OPTIONAL -> toLua(value, type.elementType(), path, provider);
            case UNION -> {
                LuaValue result = null;
                for (LuaType<?> alternative : type.alternatives()) {
                    if (matchesJava(value, alternative)) { result = toLua(value, alternative, path, provider); break; }
                }
                if (result == null) throw new LuaError("value does not match declared union at " + path);
                yield result;
            }
            case LIST -> {
                LuaTable table = new LuaTable(); int i = 1;
                for (Object element : (List<?>) value) table.rawset(i++, toLua(element, type.elementType(), path, provider));
                yield table;
            }
            case MAP -> {
                LuaTable table = new LuaTable();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) table.rawset(luaString(String.valueOf(entry.getKey())), toLua(entry.getValue(), type.elementType(), path, provider));
                yield table;
            }
            case MODEL -> modelTable(() -> value, (ModelBuilder) type.model(), path, provider);
            case INPUT_TABLE -> tableValueToLua((LuaTableValue) value, type, path, provider);
            case ANY -> anyToLua(value, path, provider);
            case FUNCTION -> throw new LuaError("callbacks cannot cross the public addon API");
            case NIL -> Constants.NIL;
        };
    }

    private LuaTable modelTable(Supplier<Object> object, ModelBuilder<Object> model, String path, String provider) throws LuaError {
        LuaTable table = new LuaTable();
        Map<String, ModelBuilder.Field<? super Object, ?>> fields = new HashMap<>();
        for (ModelBuilder.Field<? super Object, ?> field : model.fields()) fields.put(field.name(), field);
        LuaTable metatable = new LuaTable();
        metatable.rawset(modelValueKey, new LuaUserdata(new ModelValue(object, model)));
        metatable.rawset("__index", LibFunction.create((luaState, ignored, key) -> {
            ModelBuilder.Field<? super Object, ?> field = key.isString() ? fields.get(key.checkString()) : null;
            if (field == null) return Constants.NIL;
            try {
                Object instance = object.get();
                Object value = instance == null ? null : field.accessor().apply(instance);
                return toLua(value, field.type(), path + "." + field.name(), provider);
            } catch (Throwable error) { throw providerError(path + "." + field.name(), provider, error); }
        }));
        table.setMetatable(state, metatable);
        return table;
    }

    private LuaValue tableValueToLua(LuaTableValue value, LuaType<?> type, String path, String provider) throws LuaError {
        LuaTable table = new LuaTable();
        for (Map.Entry<String, Object> entry : value.values().entrySet()) {
            LuaType.InputField field = type.inputFields().get(entry.getKey());
            table.rawset(entry.getKey(), toLua(entry.getValue(), field == null ? LuaType.any() : field.type(), path, provider));
        }
        return table;
    }

    private LuaValue anyToLua(Object value, String path, String provider) throws LuaError {
        if (value == null) return Constants.NIL;
        if (value instanceof String string) return luaString(string);
        if (value instanceof Boolean bool) return ValueFactory.valueOf(bool);
        if (value instanceof Integer integer) return ValueFactory.valueOf(integer);
        if (value instanceof Number number) return ValueFactory.valueOf(number.doubleValue());
        if (value instanceof LuaTableValue table) return tableValueToLua(table, LuaType.inputTable("table", ignored -> {}), path, provider);
        if (value instanceof List<?> list) return toLua(list, LuaType.list(LuaType.any()), path, provider);
        if (value instanceof Map<?, ?> map) return toLua(map, LuaType.map(LuaType.any()), path, provider);
        return luaString(String.valueOf(value));
    }

    private boolean matchesJava(Object value, LuaType<?> type) {
        return switch (type.kind()) {
            case STRING -> value instanceof String;
            case LITERAL -> type.displayName().equals(value);
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case NIL -> value == null;
            default -> true;
        };
    }

    private Object fromLua(LuaValue value, LuaType<?> type) throws LuaError {
        return switch (type.kind()) {
            case NIL -> { if (!value.isNil()) throw new LuaError("expected nil, got " + value.typeName()); yield null; }
            case ANY -> anyFromLua(value);
            case STRING -> javaString(value);
            case INTEGER -> value.checkInteger();
            case NUMBER -> value.checkDouble();
            case BOOLEAN -> value.checkBoolean();
            case LITERAL -> { String actual = javaString(value); if (!actual.equals(type.displayName())) throw new LuaError("expected '" + type.displayName() + "'"); yield actual; }
            case OPTIONAL -> value.isNil() ? null : fromLua(value, type.elementType());
            case UNION -> unionFromLua(value, type);
            case LIST -> listFromLua(value.checkTable(), type.elementType());
            case MAP -> mapFromLua(value.checkTable(), type.elementType());
            case INPUT_TABLE -> inputTable(value.checkTable(), type);
            case MODEL -> modelFromLua(value, type);
            case FUNCTION -> throw new LuaError("callbacks cannot cross the public addon API");
        };
    }

    private Object modelFromLua(LuaValue value, LuaType<?> type) throws LuaError {
        LuaTable table = value.checkTable();
        ModelBuilder<?> model = type.model();
        Map<String, Object> fields = new LinkedHashMap<>();
        Set<String> lazyKeys = new LinkedHashSet<>();
        ModelValue source = modelValue(table);
        boolean matchingSource = source != null && source.model() == model;
        Supplier<Object> sourceInstance = matchingSource ? memoize(source.supplier()) : () -> null;
        Function<String, Object> sourceField = matchingSource
                ? key -> resolveModelField(source.model(), sourceInstance.get(), key)
                : ignored -> null;
        for (ModelBuilder.Field<?, ?> field : model.fields()) {
            LuaValue fieldValue = table.rawget(field.name());
            if (!fieldValue.isNil()) {
                fields.put(field.name(), fromLua(fieldValue, field.type()));
            } else if (matchingSource) {
                lazyKeys.add(field.name());
            } else if (!field.optional()) {
                throw new LuaError("missing field '" + field.name() + "'");
            }
        }
        try {
            if (!model.canDecode() && matchingSource) return sourceInstance.get();
            return model.decode(new LuaTableValue(fields, lazyKeys, sourceField));
        } catch (Throwable error) {
            String message = error.getMessage();
            throw new LuaError(message == null ? "invalid " + type.displayName() + " model" : message);
        }
    }

    private ModelValue modelValue(LuaTable table) {
        LuaTable metatable = table.getMetatable(state);
        if (metatable == null) return null;
        LuaValue metadata = metatable.rawget(modelValueKey);
        return metadata instanceof LuaUserdata userdata && userdata.userdata() instanceof ModelValue value
                ? value
                : null;
    }

    private Object resolveModelField(ModelBuilder<Object> model, Object instance, String key) {
        if (instance == null) return null;
        for (ModelBuilder.Field<? super Object, ?> field : model.fields()) {
            if (field.name().equals(key)) return field.accessor().apply(instance);
        }
        return null;
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        return new Supplier<>() {
            private boolean resolved;
            private T value;

            @Override public T get() {
                if (!resolved) {
                    value = supplier.get();
                    resolved = true;
                }
                return value;
            }
        };
    }

    private Object unionFromLua(LuaValue value, LuaType<?> type) throws LuaError {
        LuaError last = null;
        for (LuaType<?> alternative : type.alternatives()) {
            if (!matchesLua(value, alternative)) continue;
            try { return fromLua(value, alternative); } catch (LuaError error) { last = error; }
        }
        throw new LuaError("expected " + type.luaName() + (last == null ? "" : ", got " + value.typeName()));
    }

    /**
     * Check a value's Lua type before attempting a union conversion. Cobalt's
     * {@code checkString()} accepts numbers, so trying a string alternative
     * first would otherwise turn numeric values into decimal strings.
     */
    private boolean matchesLua(LuaValue value, LuaType<?> type) {
        return switch (type.kind()) {
            case NIL -> value.isNil();
            case STRING, LITERAL -> value.type() == Constants.TSTRING;
            case INTEGER, NUMBER -> value.type() == Constants.TNUMBER;
            case BOOLEAN -> value.type() == Constants.TBOOLEAN;
            case LIST, MAP, INPUT_TABLE -> value.type() == Constants.TTABLE;
            case FUNCTION -> value.type() == Constants.TFUNCTION;
            case OPTIONAL -> value.isNil() || matchesLua(value, type.elementType());
            case UNION -> type.alternatives().stream().anyMatch(alternative -> matchesLua(value, alternative));
            case ANY -> true;
            case MODEL -> value.type() == Constants.TTABLE;
        };
    }

    private List<Object> listFromLua(LuaTable table, LuaType<?> element) throws LuaError {
        List<Object> result = new ArrayList<>();
        for (int i = 1; !table.rawget(i).isNil(); i++) result.add(fromLua(table.rawget(i), element));
        return result;
    }

    private Map<String, Object> mapFromLua(LuaTable table, LuaType<?> element) throws LuaError {
        Map<String, Object> result = new LinkedHashMap<>(); LuaValue key = Constants.NIL;
        while (true) {
            Varargs next = table.next(key); key = next.arg(1); if (key.isNil()) break;
            result.put(javaString(key), fromLua(next.arg(2), element));
        }
        return result;
    }

    private LuaTableValue inputTable(LuaTable table, LuaType<?> type) throws LuaError {
        Map<String, Object> result = new LinkedHashMap<>();
        for (LuaType.InputField field : type.inputFields().values()) {
            LuaValue value = table.rawget(field.name());
            if (value.isNil() && field.optional()) continue;
            if (value.isNil()) throw new LuaError("missing field '" + field.name() + "'");
            result.put(field.name(), fromLua(value, field.type()));
        }
        return new LuaTableValue(result);
    }

    private Object anyFromLua(LuaValue value) throws LuaError {
        if (value.isNil()) return null;
        if (value.isString()) return javaString(value);
        if (value instanceof LuaInteger) return value.toInteger();
        if (value.isNumber()) return value.toDouble();
        if (value.type() == Constants.TBOOLEAN) return value.checkBoolean();
        if (value.type() == Constants.TTABLE) return new LuaTableValue(mapFromLua(value.checkTable(), LuaType.any()));
        return value.toString();
    }

    private static LuaString luaString(String value) {
        return ValueFactory.valueOf(value.getBytes(StandardCharsets.UTF_8));
    }

    private String javaString(LuaValue value) throws LuaError {
        LuaString string = value.checkLuaString();
        try {
            return utf8Decoder.decode(string.toBuffer()).toString();
        } catch (CharacterCodingException ignored) {
            return string.toString();
        }
    }

    private void ready() { if (state == null) throw new IllegalStateException("Cobalt runtime is not ready"); }

    private static final class Arguments implements LuaArguments {
        private final ApiRegistry.FunctionNode owner; private final List<Object> values;
        private Arguments(ApiRegistry.FunctionNode owner, List<Object> values) { this.owner = owner; this.values = values; }
        @SuppressWarnings("unchecked") public <T> T get(LuaParameter<T> parameter) {
            if (parameter.owner() != owner) throw new IllegalArgumentException("Parameter belongs to another function");
            return (T) values.get(parameter.index());
        }
    }
}
