package me.ifair.luahud.api.core;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.*;
import me.ifair.luahud.error.ErrorManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Engine-neutral, frozen definition of the complete {@code lh} API. */
public final class ApiRegistry {
    public static final String ENTRYPOINT_KEY = "luahud:api";
    public enum SpecialFunction { NONE, EVENT, INTERVAL }

    public sealed interface Node permits NamespaceNode, ValueNode, FunctionNode {
        String name(); String providerId(); LuaDoc doc();
    }

    public static final class NamespaceNode implements Node {
        private final String name, providerId;
        private final LuaDoc doc;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private NamespaceNode(String name, String providerId, LuaDoc doc) { this.name = name; this.providerId = providerId; this.doc = doc; }
        public String name() { return name; } public String providerId() { return providerId; } public LuaDoc doc() { return doc; }
        public Map<String, Node> children() { return Collections.unmodifiableMap(children); }
    }

    public record ValueNode(String name, String providerId, LuaDoc doc, LuaType<?> type, Supplier<?> supplier) implements Node {}

    public static final class FunctionNode implements Node {
        private final String name, providerId;
        private final LuaDoc doc;
        private final LuaType<?> returnType;
        private final List<LuaParameter<?>> parameters = new ArrayList<>();
        private LuaCallable<?> callable;
        private final SpecialFunction special;
        private FunctionNode(String name, String providerId, LuaDoc doc, LuaType<?> returnType, SpecialFunction special) {
            this.name = name; this.providerId = providerId; this.doc = doc; this.returnType = returnType; this.special = special;
        }
        public String name() { return name; } public String providerId() { return providerId; } public LuaDoc doc() { return doc; }
        public LuaType<?> returnType() { return returnType; } public List<LuaParameter<?>> parameters() { return List.copyOf(parameters); }
        public LuaCallable<?> callable() { return callable; } public SpecialFunction special() { return special; }
    }

    private final NamespaceNode root = new NamespaceNode("lh", "luahud", LuaDoc.NONE);
    private final List<ModelBuilder<?>> models = new ArrayList<>();
    private final Consumer<String> warnings;
    private boolean frozen;

    public ApiRegistry() { this(LuaHUD.LOGGER::warn); }
    public ApiRegistry(Consumer<String> warnings) { this.warnings = Objects.requireNonNull(warnings); }
    public LuaApiRegistrar registrar(String providerId) { mutable(); return new Registrar(providerId); }

    /** Stages a provider contribution and merges only after it returns successfully. */
    public void registerAddon(String providerId, Consumer<LuaApiRegistrar> contribution) {
        mutable();
        ApiRegistry staged = new ApiRegistry(warnings);
        contribution.accept(staged.new Registrar(providerId));
        merge(root, staged.root, "lh");
        staged.models.forEach(this::addModel);
    }

    public void discoverAddons() {
        mutable();
        for (EntrypointContainer<LuaHudApiEntrypoint> entry : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, LuaHudApiEntrypoint.class)) {
            String provider = entry.getProvider().getMetadata().getId();
            try { registerAddon(provider, entry.getEntrypoint()::register); }
            catch (Throwable error) {
                LuaHUD.LOGGER.error("Discarding Lua API contribution from '{}' because its entrypoint failed", provider, error);
                ErrorManager.reportSystem("addon " + provider, error);
            }
        }
    }

    public void addEventFunctions() {
        FunctionNode on = special("on", "Register an event callback.", SpecialFunction.EVENT);
        param(on, "event", LuaType.union(LuaType.literal("init"), LuaType.literal("tick")), "Event name");
        param(on, "callback", LuaType.function(), "Callback invoked without arguments");
        FunctionNode interval = special("setInterval", "Run a callback repeatedly after a tick interval.", SpecialFunction.INTERVAL);
        param(interval, "number", LuaType.integer(), "Interval length");
        param(interval, "type", LuaType.literal("ticks"), "Only ticks are supported");
        param(interval, "callback", LuaType.function(), "Callback invoked without arguments");
    }

    public void freeze() { mutable(); validate(root, "lh"); models.forEach(ModelBuilder::freeze); frozen = true; }
    public boolean isFrozen() { return frozen; }
    public NamespaceNode rootNode() { return root; }
    public List<ModelBuilder<?>> models() { return List.copyOf(models); }

    private FunctionNode special(String name, String doc, SpecialFunction kind) {
        FunctionNode node = new FunctionNode(name, "luahud", LuaDoc.of(doc), LuaType.nil(), kind);
        leaf(root, node, "lh." + name); return node;
    }
    private <T> void param(FunctionNode fn, String name, LuaType<T> type, String doc) {
        fn.parameters.add(new LuaParameter<>(fn, fn.parameters.size(), name, type, LuaDoc.of(doc)));
    }
    private void validate(NamespaceNode ns, String path) {
        for (Node child : ns.children.values()) {
            if (child instanceof NamespaceNode table) validate(table, path + "." + child.name());
            if (child instanceof FunctionNode fn && fn.special == SpecialFunction.NONE && fn.callable == null)
                throw new IllegalStateException("Lua API function has no implementation: " + path + "." + fn.name);
        }
    }
    private void merge(NamespaceNode target, NamespaceNode source, String path) {
        for (Node child : source.children.values()) {
            Node existing = target.children.get(child.name()); String childPath = path + "." + child.name();
            if (existing == null) target.children.put(child.name(), child);
            else if (existing instanceof NamespaceNode a && child instanceof NamespaceNode b) merge(a, b, childPath);
            else warn(child.providerId(), childPath, "duplicate or table/leaf conflict");
        }
    }
    private void addModel(ModelBuilder<?> model) {
        if (models.stream().noneMatch(x -> x.providerId().equals(model.providerId()) && x.name().equals(model.name()))) models.add(model);
    }
    private boolean leaf(NamespaceNode parent, Node child, String path) {
        if (parent.children.putIfAbsent(child.name(), child) == null) return true;
        warn(child.providerId(), path, "duplicate or table/leaf conflict"); return false;
    }
    private void warn(String provider, String path, String reason) { warnings.accept("Skipping Lua API " + reason + " from '" + provider + "' at " + path + "; first definition wins"); }
    private void mutable() { if (frozen) throw new IllegalStateException("Lua API registration is closed; the registry has already been frozen"); }
    private static void name(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid Lua identifier: " + value); }

    private final class Registrar implements LuaApiRegistrar {
        private final String provider;
        private Registrar(String provider) { this.provider = Objects.requireNonNull(provider); }
        public NamespaceBuilder root() { mutable(); return new Namespace(provider, root, "lh"); }
        public <T> ModelBuilder<T> model(String name, Class<T> type) { mutable(); ModelBuilder<T> model = new ModelBuilder<>(provider, name, type); addModel(model); return model; }
    }

    private final class Namespace implements NamespaceBuilder {
        private final String provider, path; private final NamespaceNode node;
        private Namespace(String provider, NamespaceNode node, String path) { this.provider = provider; this.node = node; this.path = path; }
        public NamespaceBuilder namespace(String childName, Consumer<NamespaceBuilder> definition) {
            mutable(); name(childName); String childPath = path + "." + childName; Node old = node.children.get(childName);
            if (old != null && !(old instanceof NamespaceNode)) { warn(provider, childPath, "table/leaf conflict"); return this; }
            NamespaceNode child = old instanceof NamespaceNode table ? table : new NamespaceNode(childName, provider, LuaDoc.NONE);
            if (old == null) node.children.put(childName, child);
            definition.accept(new Namespace(provider, child, childPath)); return this;
        }
        public <T> NamespaceBuilder value(String childName, LuaType<T> type, LuaDoc doc, Supplier<? extends T> supplier) {
            mutable(); name(childName); leaf(node, new ValueNode(childName, provider, doc, type, supplier), path + "." + childName); return this;
        }
        public <R> FunctionBuilder<R> function(String childName, LuaType<R> result, String doc) {
            mutable(); name(childName); FunctionNode fn = new FunctionNode(childName, provider, LuaDoc.of(doc), result, SpecialFunction.NONE);
            return leaf(node, fn, path + "." + childName) ? new Function<>(fn) : new IgnoredFunction<>();
        }
    }

    private static final class Function<R> implements FunctionBuilder<R> {
        private final FunctionNode node; private Function(FunctionNode node) { this.node = node; }
        public <T> LuaParameter<T> parameter(String name, LuaType<T> type, String doc) {
            if (type.kind() == LuaType.Kind.FUNCTION) throw new IllegalArgumentException("Addon functions cannot receive Lua callbacks");
            LuaParameter<T> p = new LuaParameter<>(node, node.parameters.size(), name, type, LuaDoc.of(doc)); node.parameters.add(p); return p;
        }
        public <T> LuaParameter<T> optionalParameter(String name, LuaType<T> type, String doc) { return parameter(name, LuaType.optional(type), doc); }
        public FunctionBuilder<R> call(LuaCallable<R> callable) { if (node.callable != null) throw new IllegalStateException("Function already implemented"); node.callable = Objects.requireNonNull(callable); return this; }
    }
    private static final class IgnoredFunction<R> implements FunctionBuilder<R> {
        private int index; private final Object owner = new Object();
        public <T> LuaParameter<T> parameter(String n, LuaType<T> t, String d) { return new LuaParameter<>(owner, index++, n, t, LuaDoc.of(d)); }
        public <T> LuaParameter<T> optionalParameter(String n, LuaType<T> t, String d) { return parameter(n, LuaType.optional(t), d); }
        public FunctionBuilder<R> call(LuaCallable<R> c) { return this; }
    }
}
