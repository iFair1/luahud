package dev.ifair.luahud.api.core;

import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;

import java.util.*;

/** Shared type names and input contracts for generated API artifacts. */
final class ApiTypeContext {
    record InputModel(ModelBuilder<?> model) {}

    private static String safe(String value) {
        String result = value.replaceAll("[^A-Za-z0-9_]", "_");
        return result.isEmpty() || Character.isDigit(result.charAt(0)) ? "_" + result : result;
    }

    final Map<Object, String> namedTypes = new IdentityHashMap<>();
    final Map<LuaType<?>, String> owners = new IdentityHashMap<>();
    private final Map<ModelBuilder<?>, InputModel> inputModels = new IdentityHashMap<>();
    private final Set<Object> collected = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> names = new HashSet<>();
    private final boolean integersAsNumbers;

    ApiTypeContext(ApiRegistry registry) {
        this(registry, false);
    }

    ApiTypeContext(ApiRegistry registry, boolean integersAsNumbers) {
        this.integersAsNumbers = integersAsNumbers;
        for (ModelBuilder<?> model : registry.models()) add(model, model.providerId(), model.name());
        registry.models().forEach(model -> collect(model.type(), model.providerId()));
        collect(registry.rootNode());
        registry.events().forEach(event -> collect(event.payloadType(), event.providerId()));
    }
    private void collect(ApiRegistry.NamespaceNode node) { for (ApiRegistry.Node child : node.children().values()) { if (child instanceof ApiRegistry.NamespaceNode table) collect(table); else if (child instanceof ApiRegistry.ValueNode value) collect(value.type(), value.providerId()); else if (child instanceof ApiRegistry.FunctionNode function) { collect(function.returnType(), function.providerId()); function.parameters().forEach(parameter -> collect(parameter.type(), function.providerId())); } } }
    private void collect(LuaType<?> type, String provider) {
        if (type == null) return;
        if (type.kind() == LuaType.Kind.MODEL || type.kind() == LuaType.Kind.MODEL_INPUT) {
            ModelBuilder<?> model = type.model();
            add(model, model.providerId(), model.name());
            InputModel input = inputModels.computeIfAbsent(model, InputModel::new);
            if (model.canDecode()) add(input, model.providerId(), model.name() + "Input");
            if (!collected.add(model)) return;
            model.fields().forEach(field -> collect(field.type(), model.providerId()));
        }
        if (type.kind() == LuaType.Kind.INPUT_TABLE || type.kind() == LuaType.Kind.ALIAS) {
            owners.put(type, provider);
            add(type, provider, type.displayName());
            if (!collected.add(type)) return;
            type.inputFields().values().forEach(field -> collect(field.type(), provider));
        }
        if (type.elementType() != null) collect(type.elementType(), provider);
        type.alternatives().forEach(value -> collect(value, provider));
    }

    String inputFieldType(LuaType<?> type, String provider) {
        return switch (type.kind()) {
            case MODEL, MODEL_INPUT -> inputType(type.model());
            case OPTIONAL -> "(" + inputFieldType(type.elementType(), provider) + ")?";
            case LIST -> "(" + inputFieldType(type.elementType(), provider) + ")[]";
            case MAP -> "table<string, " + inputFieldType(type.elementType(), provider) + ">";
            case UNION -> String.join(" | ", type.alternatives().stream().map(value -> inputFieldType(value, provider)).toList());
            default -> type(type, provider);
        };
    }

    private String inputType(ModelBuilder<?> model) {
        String output = namedTypes.get(model);
        return model.canDecode() ? output + " | " + namedTypes.get(inputModels.get(model)) : output;
    }

    private void add(Object key, String provider, String friendly) { if (namedTypes.containsKey(key)) return; String base = provider.equals("luahud") ? safe(friendly) : safe(provider) + "_" + safe(friendly); String name = base; int suffix = 2; while (!names.add(name)) name = base + "_" + suffix++; namedTypes.put(key, name); }
    private String groupedType(LuaType<?> type, String provider) {
        String value = type(type, provider);
        return type.kind() == LuaType.Kind.UNION || type.kind() == LuaType.Kind.MODEL_INPUT && type.model().canDecode()
                ? "(" + value + ")" : value;
    }

    String type(LuaType<?> type, String provider) {
        return switch (type.kind()) {
            case INTEGER -> integersAsNumbers ? "number" : "integer";
            case MODEL -> namedTypes.get(type.model());
            case MODEL_INPUT -> inputType(type.model());
            case INPUT_TABLE, ALIAS -> namedTypes.get(type);
            case OPTIONAL -> groupedType(type.elementType(), provider) + "?";
            case UNION -> String.join(" | ", type.alternatives().stream().map(value -> type(value, provider)).toList());
            case LIST -> groupedType(type.elementType(), provider) + "[]";
            case MAP -> "table<string, " + type(type.elementType(), provider) + ">";
            default -> type.luaName();
        };
    }
}
