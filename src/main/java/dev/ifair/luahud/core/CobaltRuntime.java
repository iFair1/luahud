package dev.ifair.luahud.core;

import dev.ifair.luahud.api.*;
import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.modules.RenderModule;
import dev.ifair.luahud.render.RenderBuffer;
import dev.ifair.luahud.error.ErrorManager;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHook;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongSupplier;

/** Owns the Lua state, API bindings, and callback execution lifecycle. */
public final class CobaltRuntime {
    private static final int DEFAULT_TIMEOUT_MS = 25;
    private static final int WATCHDOG_HOOK_INTERVAL = 1000;

    private record Callback(LuaFunction function, String profile) {}
    private record Timer(int interval, String event, Callback callback, int[] elapsed) {}

    private final ApiRegistry registry;
    private final Runnable invocationBoundary;
    private final int timeoutMs;
    private final LongSupplier nanoTime;
    private final Map<String, List<Callback>> listeners = new HashMap<>();
    private final List<Timer> timers = new ArrayList<>();
    private final Map<String, Object> loadOwners = new HashMap<>();
    private LuaState state;
    private LuaValueConverter converter;
    private String currentProfile;
    private boolean watchdogActive;
    private boolean watchdogTimedOut;
    private long watchdogDeadline;

    private record WatchdogState(boolean active, boolean timedOut, long deadline) {}

    public CobaltRuntime(ApiRegistry registry) {
        this(registry, () -> {}, DEFAULT_TIMEOUT_MS);
    }

    public CobaltRuntime(ApiRegistry registry, Runnable invocationBoundary) {
        this(registry, invocationBoundary, DEFAULT_TIMEOUT_MS);
    }

    public CobaltRuntime(ApiRegistry registry, Runnable invocationBoundary, int timeoutMs) {
        this(registry, invocationBoundary, timeoutMs, System::nanoTime);
    }

    CobaltRuntime(ApiRegistry registry, Runnable invocationBoundary, int timeoutMs, LongSupplier nanoTime) {
        if (!registry.isFrozen()) throw new IllegalArgumentException("Lua API registry must be frozen before starting Cobalt");
        if (timeoutMs < 0) throw new IllegalArgumentException("Lua timeout must not be negative");
        this.registry = registry;
        this.invocationBoundary = Objects.requireNonNull(invocationBoundary, "invocationBoundary");
        this.timeoutMs = timeoutMs;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public void start() throws LuaError {
        start(null);
    }

    public void start(Path scriptsDirectory) throws LuaError {
        listeners.clear(); timers.clear(); loadOwners.clear();
        RenderBuffer.clear();
        RenderModule.resetSection();
        state = new LuaState();
        converter = new LuaValueConverter(state);
        if (timeoutMs > 0) {
            state.getMainThread().getDebugState().setHook(new WatchdogHook(), false, false, false,
                    WATCHDOG_HOOK_INTERVAL);
        }
        CoreLibraries.standardGlobals(state);
        LuaModules.install(state, scriptsDirectory);
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
        RenderModule.beginInvocation(loadOwners.computeIfAbsent(profile, ignored -> new Object()));
        try {
            invocationBoundary.run();
            WatchdogState previousWatchdog = beginWatchdog();
            try {
                Varargs result = LuaThread.runMain(state, LoadState.load(state, source, name, state.globals()));
                throwIfTimedOut();
                return result;
            } catch (LuaError error) {
                if (watchdogTimedOut) removeProfileRegistrations(profile);
                throw error;
            } finally {
                endWatchdog(previousWatchdog);
            }
        } finally {
            RenderModule.endInvocation();
            currentProfile = previousProfile;
        }
    }

    public LuaValue global(String name) { ready(); return state.globals().rawget(name); }

    public void fireEvent(String eventName) { fireEvent(eventName, null); }

    public void fireEvent(String eventName, Object payload) {
        dispatchEvent(registry.event(eventName), payload);
    }

    public void fireEvent(EventType<Void> event) { dispatchEvent(event, null); }

    public <T> void fireEvent(EventType<T> event, T payload) { dispatchEvent(event, payload); }

    private void dispatchEvent(EventType<?> event, Object payload) {
        registry.validateEvent(event, payload);
        if (event.name().equals("error") && event.providerId().equals("luahud")) {
            ErrorManager.withoutEvents(() -> dispatchCallbacks(event, payload));
        } else {
            dispatchCallbacks(event, payload);
        }
    }

    private void dispatchCallbacks(EventType<?> event, Object payload) {
        List<Callback> callbacks = listeners.get(event.name());
        List<Callback> callbackSnapshot = callbacks == null ? List.of() : List.copyOf(callbacks);
        for (Timer timer : List.copyOf(timers)) {
            if (!timer.event.equals(event.name())) continue;
            if (++timer.elapsed[0] >= timer.interval) {
                timer.elapsed[0] = 0;
                if (invokeCallback(timer.callback, null, event, payload)) timers.removeIf(candidate -> candidate == timer);
            }
        }
        for (Callback callback : callbackSnapshot) {
            if (invokeCallback(callback, event.name(), event, payload)) {
                callbacks.removeIf(candidate -> candidate == callback);
            }
        }
        if (callbacks != null && callbacks.isEmpty()) listeners.remove(event.name());
    }

    /** @return whether this callback timed out and should be removed. */
    private boolean invokeCallback(Callback callback, String cooldownKey, EventType<?> event, Object payload) {
        String previousProfile = currentProfile;
        currentProfile = callback.profile;
        RenderModule.beginInvocation(callback);
        try {
            invocationBoundary.run();
            WatchdogState previousWatchdog = beginWatchdog();
            try {
                Varargs arguments = event != null && event.hasPayload()
                        ? converter.toLua(payload, event.payloadType(), "event " + event.name(), event.providerId())
                        : Constants.NONE;
                LuaThread.run(new LuaThread(state, callback.function), arguments);
                throwIfTimedOut();
                return false;
            } catch (LuaError error) {
                ErrorManager.reportProfile(callback.profile, cooldownKey == null ? "timer" : cooldownKey, error);
                return watchdogTimedOut;
            } finally {
                endWatchdog(previousWatchdog);
            }
        } finally {
            RenderModule.endInvocation();
            currentProfile = previousProfile;
        }
    }

    private WatchdogState beginWatchdog() {
        WatchdogState previous = new WatchdogState(watchdogActive, watchdogTimedOut, watchdogDeadline);
        watchdogTimedOut = false;
        if (timeoutMs > 0) {
            long deadline = nanoTime.getAsLong() + timeoutMs * 1_000_000L;
            // A nested publication must not extend the calling callback's execution budget.
            if (!watchdogActive || deadline - watchdogDeadline < 0) watchdogDeadline = deadline;
            watchdogActive = true;
        }
        return previous;
    }

    private void endWatchdog(WatchdogState previous) {
        watchdogActive = previous.active();
        watchdogTimedOut = previous.timedOut();
        watchdogDeadline = previous.deadline();
    }

    private void throwIfTimedOut() throws LuaError {
        if (timeoutMs == 0 || watchdogTimedOut) {
            if (watchdogTimedOut) throw timeoutError();
            return;
        }
        if (nanoTime.getAsLong() - watchdogDeadline >= 0) {
            watchdogTimedOut = true;
            throw timeoutError();
        }
    }

    private LuaError timeoutError() {
        return new LuaError("Lua execution exceeded " + timeoutMs + " ms and was stopped");
    }

    void unloadProfile(String profile) {
        Object owner = loadOwners.remove(profile);
        if (owner != null) RenderBuffer.removeOwner(owner);
        listeners.values().forEach(callbacks -> callbacks.stream()
                .filter(callback -> Objects.equals(callback.profile, profile)).forEach(RenderBuffer::removeOwner));
        timers.stream().filter(timer -> Objects.equals(timer.callback.profile, profile))
                .forEach(timer -> RenderBuffer.removeOwner(timer.callback));
        removeProfileRegistrations(profile);
    }

    private void removeProfileRegistrations(String profile) {
        listeners.values().forEach(callbacks -> callbacks.removeIf(callback -> Objects.equals(callback.profile, profile)));
        listeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        timers.removeIf(timer -> Objects.equals(timer.callback.profile, profile));
    }

    private final class WatchdogHook implements DebugHook {
        @Override
        public void onCount(LuaState state, DebugState debugState, DebugFrame frame) throws LuaError {
            if (!watchdogActive || nanoTime.getAsLong() - watchdogDeadline < 0) return;
            watchdogTimedOut = true;
            throw timeoutError();
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
                return converter.supplied(value.supplier(), value.type(), path + "." + value.name(), value.providerId());
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
                    try { registry.event(event); }
                    catch (IllegalArgumentException error) { throw new LuaError(error.getMessage()); }
                    listeners.computeIfAbsent(event, ignored -> new ArrayList<>())
                            .add(new Callback(args.arg(2).checkFunction(), currentProfile));
                    return Constants.NIL;
                }
                if (definition.special() == ApiRegistry.SpecialFunction.INTERVAL) {
                    int interval = args.arg(1).checkInteger();
                    String event = args.arg(2).checkString();
                    try { registry.event(event); }
                    catch (IllegalArgumentException error) { throw new LuaError(error.getMessage()); }
                    timers.add(new Timer(interval, event, new Callback(args.arg(3).checkFunction(), currentProfile), new int[]{0}));
                    return Constants.NIL;
                }

                List<Object> converted = new ArrayList<>(definition.parameters().size());
                for (int i = 0; i < definition.parameters().size(); i++) {
                    LuaParameter<?> parameter = definition.parameters().get(i);
                    try { converted.add(converter.fromLua(args.arg(i + 1), parameter.type())); }
                    catch (LuaError error) { throw new LuaError("bad argument #" + (i + 1) + " ('" + parameter.name() + "'): " + error.getMessage()); }
                }
                LuaArguments arguments = new Arguments(definition, converted);
                Object result = definition.callable().call(arguments);
                return converter.toLua(result, definition.returnType(), path, definition.providerId());
            } catch (LuaError error) {
                throw error;
            } catch (Throwable error) {
                throw LuaValueConverter.providerError(path, definition.providerId(), error);
            }
        });
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
