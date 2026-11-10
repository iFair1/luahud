package dev.ifair.luahud.api.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ifair.luahud.api.EventType;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;

import java.util.Comparator;

/** Exports the frozen Lua API using the documentation site's version 3 reference schema. */
public final class DocumentationGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final ApiTypeContext context;

    private DocumentationGenerator(ApiRegistry registry) {
        context = new ApiTypeContext(registry, false, false);
    }

    /** Generates metadata without evaluating value suppliers, accessors, or functions. */
    public static String generate(ApiRegistry registry) {
        if (!registry.isFrozen()) {
            throw new IllegalArgumentException("Registry must be frozen before generating its documentation");
        }
        DocumentationGenerator generator = new DocumentationGenerator(registry);
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 3);
        document.add("root", generator.node(registry.rootNode(), "lh"));
        document.add("types", generator.definitions());
        JsonArray events = new JsonArray();
        registry.events().stream().sorted(Comparator.comparing(EventType::name)).forEach(event -> {
            JsonObject item = new JsonObject();
            item.addProperty("name", event.name());
            item.addProperty("description", event.doc().description());
            item.add("payloadType", generator.type(event.payloadType()));
            events.add(item);
        });
        document.add("events", events);
        return GSON.toJson(document) + "\n";
    }

    private JsonObject node(ApiRegistry.Node node, String path) {
        JsonObject item = new JsonObject();
        item.addProperty("name", node.name());
        item.addProperty("path", path);
        item.addProperty("description", node.doc().description());
        if (node instanceof ApiRegistry.NamespaceNode namespace) {
            item.addProperty("kind", "namespace");
            JsonArray children = new JsonArray();
            namespace.children().values().stream().sorted(Comparator.comparing(ApiRegistry.Node::name))
                    .forEach(child -> children.add(node(child, path + "." + child.name())));
            item.add("children", children);
        } else if (node instanceof ApiRegistry.ValueNode value) {
            item.addProperty("kind", "property");
            item.add("type", type(value.type()));
        } else if (node instanceof ApiRegistry.FunctionNode function) {
            item.addProperty("kind", "function");
            JsonArray parameters = new JsonArray();
            for (LuaParameter<?> parameter : function.parameters()) {
                JsonObject field = new JsonObject();
                field.addProperty("name", parameter.name());
                field.addProperty("description", parameter.doc().description());
                boolean optional = parameter.type().kind() == LuaType.Kind.OPTIONAL;
                field.addProperty("optional", optional);
                // Argument omission is represented by the parameter flag; nullable values
                // elsewhere retain their optional type wrapper.
                field.add("type", type(optional ? parameter.type().elementType() : parameter.type()));
                parameters.add(field);
            }
            item.add("parameters", parameters);
            item.add("returnType", type(function.returnType()));
        }
        return item;
    }

    private JsonObject type(LuaType<?> type) {
        JsonObject item = new JsonObject();
        switch (type.kind()) {
            case NIL, ANY, STRING, INTEGER, NUMBER, BOOLEAN, FUNCTION -> {
                item.addProperty("kind", "primitive");
                item.addProperty("name", type.luaName());
            }
            case MODEL, INPUT_TABLE, ALIAS -> {
                return reference(context.namedTypes.get(type.kind() == LuaType.Kind.MODEL ? type.model() : type));
            }
            case MODEL_INPUT -> {
                return modelInput(type.model());
            }
            case OPTIONAL, LIST -> {
                item.addProperty("kind", type.kind() == LuaType.Kind.OPTIONAL ? "optional" : "list");
                item.add("element", type(type.elementType()));
            }
            case MAP -> {
                item.addProperty("kind", "map");
                item.add("key", type(type.keyType()));
                item.add("value", type(type.elementType()));
            }
            case UNION -> {
                item.addProperty("kind", "union");
                JsonArray alternatives = new JsonArray();
                type.alternatives().forEach(value -> alternatives.add(type(value)));
                item.add("alternatives", alternatives);
            }
            case LITERAL -> {
                item.addProperty("kind", "literal");
                item.addProperty("value", type.displayName());
            }
        }
        return item;
    }

    private JsonArray definitions() {
        JsonArray definitions = new JsonArray();
        context.namedTypes.entrySet().stream().sorted(java.util.Map.Entry.comparingByValue()).forEach(entry -> {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.getValue());
            Object definition = entry.getKey();
            JsonArray fields = new JsonArray();
            if (definition instanceof ModelBuilder<?> model) {
                item.addProperty("kind", "model");
                item.addProperty("name", model.name());
                model.fields().stream().sorted(Comparator.comparing(ModelBuilder.Field::name)).forEach(field ->
                        fields.add(field(field.name(), field.doc().description(), type(field.type()), field.optional())));
            } else if (definition instanceof ApiTypeContext.InputModel input) {
                item.addProperty("kind", "input");
                item.addProperty("name", input.model().name() + "Input");
                item.add("model", reference(context.namedTypes.get(input.model())));
                input.model().fields().stream().filter(field -> field.inputMode() != ModelBuilder.InputMode.OUTPUT_ONLY)
                        .sorted(Comparator.comparing(ModelBuilder.Field::name)).forEach(field ->
                                fields.add(field(field.name(), field.doc().description(), inputFieldType(field.type()),
                                        field.inputMode() != ModelBuilder.InputMode.REQUIRED)));
            } else if (definition instanceof LuaType<?> named) {
                item.addProperty("name", named.displayName());
                if (named.kind() == LuaType.Kind.ALIAS) {
                    item.addProperty("kind", "alias");
                    item.addProperty("description", named.doc().description());
                    item.add("type", type(named.elementType()));
                    definitions.add(item);
                    return;
                }
                item.addProperty("kind", "input");
                named.inputFields().values().stream().sorted(Comparator.comparing(LuaType.InputField::name)).forEach(field ->
                        fields.add(field(field.name(), field.doc().description(), type(field.type()), field.optional())));
            }
            item.add("fields", fields);
            definitions.add(item);
        });
        return definitions;
    }

    private static JsonObject field(String name, String description, JsonObject type, boolean optional) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("description", description);
        field.addProperty("optional", optional);
        field.add("type", type);
        return field;
    }

    private JsonObject modelInput(ModelBuilder<?> model) {
        JsonObject output = reference(context.namedTypes.get(model));
        if (!model.canDecode()) return output;
        JsonObject union = new JsonObject();
        union.addProperty("kind", "union");
        JsonArray alternatives = new JsonArray();
        alternatives.add(output);
        alternatives.add(reference(context.inputModelName(model)));
        union.add("alternatives", alternatives);
        return union;
    }

    /** Model fields are structural inputs recursively, including models nested in collections. */
    private JsonObject inputFieldType(LuaType<?> type) {
        if (type.kind() == LuaType.Kind.MODEL || type.kind() == LuaType.Kind.MODEL_INPUT) return modelInput(type.model());
        JsonObject item = new JsonObject();
        switch (type.kind()) {
            case OPTIONAL, LIST -> {
                item.addProperty("kind", type.kind() == LuaType.Kind.OPTIONAL ? "optional" : "list");
                item.add("element", inputFieldType(type.elementType()));
            }
            case MAP -> {
                item.addProperty("kind", "map");
                item.add("key", type(type.keyType()));
                item.add("value", inputFieldType(type.elementType()));
            }
            case UNION -> {
                item.addProperty("kind", "union");
                JsonArray alternatives = new JsonArray();
                type.alternatives().forEach(part -> alternatives.add(inputFieldType(part)));
                item.add("alternatives", alternatives);
            }
            default -> { return type(type); }
        }
        return item;
    }

    private static JsonObject reference(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("kind", "reference");
        item.addProperty("id", id);
        return item;
    }
}
