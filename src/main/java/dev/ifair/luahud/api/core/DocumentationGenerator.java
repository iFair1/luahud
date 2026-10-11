package dev.ifair.luahud.api.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/** Exports the frozen Lua API as data for a documentation site's reference pages. */
public final class DocumentationGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DocumentationGenerator() {}

    /** Generates metadata without evaluating value suppliers, accessors, or functions. */
    public static String generate(ApiRegistry registry) {
        if (!registry.isFrozen()) {
            throw new IllegalArgumentException("Registry must be frozen before generating its documentation");
        }
        ApiTypeContext context = new ApiTypeContext(registry);
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 1);
        document.addProperty("root", "lh");
        JsonArray namespaces = new JsonArray();
        JsonArray properties = new JsonArray();
        JsonArray functions = new JsonArray();
        collect(registry.rootNode(), "lh", context, namespaces, properties, functions);
        document.add("namespaces", namespaces);
        document.add("properties", properties);
        document.add("functions", functions);

        JsonArray types = new JsonArray();
        context.namedTypes.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
            Object definition = entry.getKey();
            JsonObject type = new JsonObject();
            type.addProperty("name", entry.getValue());
            JsonArray fields = new JsonArray();
            if (definition instanceof ModelBuilder<?> model) {
                type.addProperty("kind", "model");
                type.addProperty("providerId", model.providerId());
                type.addProperty("acceptsTable", model.canDecode());
                model.fields().stream().sorted(Comparator.comparing(ModelBuilder.Field::name)).forEach(field -> {
                    JsonObject item = field(field.name(), field.doc().description(), field.type(),
                            field.optional(), model.providerId(), context, false);
                    item.addProperty("inputMode", lower(field.inputMode()));
                    fields.add(item);
                });
            } else if (definition instanceof ApiTypeContext.InputModel input) {
                ModelBuilder<?> model = input.model();
                type.addProperty("kind", "model_input");
                type.addProperty("providerId", model.providerId());
                type.addProperty("outputType", context.namedTypes.get(model));
                model.fields().stream().filter(field -> field.inputMode() != ModelBuilder.InputMode.OUTPUT_ONLY)
                        .sorted(Comparator.comparing(ModelBuilder.Field::name)).forEach(field -> {
                            JsonObject item = field(field.name(), field.doc().description(), field.type(),
                                    field.inputMode() != ModelBuilder.InputMode.REQUIRED, model.providerId(), context, true);
                            item.addProperty("inputMode", lower(field.inputMode()));
                            fields.add(item);
                        });
            } else if (definition instanceof LuaType<?> table) {
                String provider = context.owners.get(table);
                type.addProperty("kind", "input_table");
                type.addProperty("providerId", provider);
                table.inputFields().values().stream().sorted(Comparator.comparing(LuaType.InputField::name))
                        .forEach(field -> fields.add(field(field.name(), field.doc().description(), field.type(),
                                field.optional(), provider, context, false)));
            }
            type.add("fields", fields);
            types.add(type);
        });
        document.add("types", types);
        return GSON.toJson(document) + "\n";
    }

    private static void collect(ApiRegistry.NamespaceNode namespace, String path, ApiTypeContext context,
                                JsonArray namespaces, JsonArray properties, JsonArray functions) {
        namespaces.add(node(namespace, path));
        namespace.children().values().stream().sorted(Comparator.comparing(ApiRegistry.Node::name)).forEach(child -> {
            String childPath = path + "." + child.name();
            if (child instanceof ApiRegistry.NamespaceNode nested) {
                collect(nested, childPath, context, namespaces, properties, functions);
            } else if (child instanceof ApiRegistry.ValueNode value) {
                JsonObject property = node(value, childPath);
                property.add("type", type(value.type(), value.providerId(), context, false));
                properties.add(property);
            } else if (child instanceof ApiRegistry.FunctionNode function) {
                JsonObject item = node(function, childPath);
                JsonArray parameters = new JsonArray();
                for (LuaParameter<?> parameter : function.parameters()) {
                    parameters.add(field(parameter.name(), parameter.doc().description(), parameter.type(),
                            parameter.type().kind() == LuaType.Kind.OPTIONAL, function.providerId(), context, false));
                }
                item.add("parameters", parameters);
                item.add("returnType", type(function.returnType(), function.providerId(), context, false));
                functions.add(item);
            }
        });
    }

    private static JsonObject node(ApiRegistry.Node node, String path) {
        JsonObject item = new JsonObject();
        item.addProperty("path", path);
        item.addProperty("name", node.name());
        item.addProperty("providerId", node.providerId());
        item.addProperty("description", node.doc().description());
        return item;
    }

    private static JsonObject field(String name, String description, LuaType<?> type, boolean optional,
                                    String provider, ApiTypeContext context, boolean input) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("description", description);
        field.addProperty("optional", optional);
        field.add("type", type(type, provider, context, input));
        return field;
    }

    private static JsonObject type(LuaType<?> type, String provider, ApiTypeContext context, boolean input) {
        JsonObject item = new JsonObject();
        boolean modelInput = type.kind() == LuaType.Kind.MODEL_INPUT || input && type.kind() == LuaType.Kind.MODEL;
        item.addProperty("kind", modelInput ? "model_input" : lower(type.kind()));
        item.addProperty("display", input ? context.inputFieldType(type, provider) : context.type(type, provider));
        if (type.elementType() != null) {
            item.add("elementType", type(type.elementType(), provider, context, input));
        }
        if (type.kind() == LuaType.Kind.UNION) {
            JsonArray alternatives = new JsonArray();
            type.alternatives().forEach(value -> alternatives.add(type(value, provider, context, input)));
            item.add("alternatives", alternatives);
        }
        if (type.kind() == LuaType.Kind.LITERAL) item.addProperty("value", type.displayName());
        if (type.model() != null || type.kind() == LuaType.Kind.INPUT_TABLE) {
            JsonArray references = new JsonArray();
            if (type.model() != null) {
                references.add(context.namedTypes.get(type.model()));
                if (modelInput && type.model().canDecode()) {
                    context.namedTypes.entrySet().stream()
                            .filter(entry -> entry.getKey() instanceof ApiTypeContext.InputModel definition
                                    && definition.model() == type.model())
                            .forEach(entry -> references.add(entry.getValue()));
                }
            } else {
                references.add(context.namedTypes.get(type));
            }
            item.add("references", references);
        }
        return item;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
