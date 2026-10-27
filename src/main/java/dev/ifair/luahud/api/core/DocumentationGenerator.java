package dev.ifair.luahud.api.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;

import java.util.Comparator;

/** Exports the frozen Lua API using the documentation site's version 2 reference schema. */
public final class DocumentationGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DocumentationGenerator() {}

    /** Generates metadata without evaluating value suppliers, accessors, or functions. */
    public static String generate(ApiRegistry registry) {
        if (!registry.isFrozen()) {
            throw new IllegalArgumentException("Registry must be frozen before generating its documentation");
        }
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 2);
        document.add("root", node(registry.rootNode(), "lh"));
        return GSON.toJson(document) + "\n";
    }

    private static JsonObject node(ApiRegistry.Node node, String path) {
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

    private static JsonObject type(LuaType<?> type) {
        JsonObject item = new JsonObject();
        switch (type.kind()) {
            case NIL, ANY, STRING, INTEGER, NUMBER, BOOLEAN, FUNCTION -> {
                item.addProperty("kind", "primitive");
                item.addProperty("name", type.luaName());
            }
            case MODEL, INPUT_TABLE, ALIAS -> {
                return reference(type.displayName());
            }
            case MODEL_INPUT -> {
                JsonObject output = reference(type.model().name());
                if (!type.model().canDecode()) return output;
                item.addProperty("kind", "union");
                JsonArray alternatives = new JsonArray();
                alternatives.add(output);
                alternatives.add(reference(type.model().name() + "Input"));
                item.add("alternatives", alternatives);
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

    private static JsonObject reference(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("kind", "reference");
        item.addProperty("id", id);
        return item;
    }
}
