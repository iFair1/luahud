package dev.ifair.luahud.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.ifair.luahud.api.LuaTableValue;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.Map;
import java.util.List;
import java.util.Set;

/** Converts Lua's modern Minecraft text component tables to the running game's codec. */
public final class ChatText {
    private ChatText() {}

    public static Text decode(Object value, RegistryWrapper.WrapperLookup registries) {
        if (value instanceof String text) return Text.literal(text);
        JsonElement json = toJson(value, "message", 0);
        adaptComponent(json);
        DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : registries.getOps(JsonOps.INSTANCE);
        return TextCodecs.CODEC.parse(ops, json).getOrThrow(
                error -> new IllegalArgumentException("Invalid chat text: " + error));
    }

    private static JsonElement toJson(Object value, String field, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Chat text nesting exceeds 64 levels");
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Chat text numbers must be finite");
            return new JsonPrimitive(number);
        }
        if (value instanceof List<?> values) {
            JsonArray result = new JsonArray();
            for (Object element : values) result.add(toJson(element, field, depth + 1));
            return result;
        }
        Map<?, ?> values;
        if (value instanceof LuaTableValue table) values = table.values();
        else if (value instanceof Map<?, ?> map) values = map;
        else throw new IllegalArgumentException("Expected a text component value in " + field);

        boolean array = !values.isEmpty() && values.keySet().stream()
                .allMatch(key -> key instanceof String name && name.matches("[1-9][0-9]*"));
        if (array || values.isEmpty() && Set.of("extra", "with", "shadow_color").contains(field)) {
            JsonArray result = new JsonArray();
            for (int i = 1; i <= values.size(); i++) {
                if (!values.containsKey(Integer.toString(i))) {
                    throw new IllegalArgumentException("Chat text arrays must have consecutive indices starting at 1");
                }
                result.add(toJson(values.get(Integer.toString(i)), field, depth + 1));
            }
            return result;
        }
        JsonObject result = new JsonObject();
        for (var entry : values.entrySet()) {
            String name = (String) entry.getKey();
            if (name.matches("[0-9]+")) throw new IllegalArgumentException("Cannot mix array and object fields in chat text");
            result.add(name, toJson(entry.getValue(), name, depth + 1));
        }
        return result;
    }

    private static void adaptComponent(JsonElement value) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(ChatText::adaptComponent);
            return;
        }
        if (!value.isJsonObject()) return;
        JsonObject text = value.getAsJsonObject();
        if (text.has("clickEvent") || text.has("hoverEvent")) {
            throw new IllegalArgumentException("Use click_event and hover_event in chat text");
        }
        for (String field : new String[] {"extra", "with"}) {
            if (text.has(field)) adaptComponent(text.get(field));
        }
        if (text.has("hover_event")) {
            JsonObject hover = text.getAsJsonObject("hover_event");
            String action = hover.get("action").getAsString();
            if (action.equals("show_text") && hover.has("value")) adaptComponent(hover.get("value"));
            if (action.equals("show_entity") && hover.has("name")) adaptComponent(hover.get("name"));
        }
        //? if <1.21.5 {
        adaptLegacyEvents(text);
        //?}
        //? if <1.21.2 {
        if (text.has("shadow_color")) {
            throw new IllegalArgumentException("shadow_color requires Minecraft 1.21.2 or newer");
        }
        //?}
    }

    //? if <1.21.5 {
    private static void adaptLegacyEvents(JsonObject text) {
        if (text.has("click_event")) {
            JsonObject click = text.remove("click_event").getAsJsonObject();
            String action = click.get("action").getAsString();
            String field = switch (action) {
                case "open_url" -> "url";
                case "suggest_command" -> "command";
                case "change_page" -> "page";
                case "copy_to_clipboard" -> "value";
                default -> throw new IllegalArgumentException("Unsupported click action on this Minecraft version: " + action);
            };
            if (!click.has(field)) throw new IllegalArgumentException("Missing click_event field: " + field);
            JsonObject legacy = new JsonObject();
            legacy.addProperty("action", action);
            legacy.addProperty("value", click.get(field).getAsString());
            text.add("clickEvent", legacy);
        }
        if (text.has("hover_event")) {
            JsonObject hover = text.remove("hover_event").getAsJsonObject();
            String action = hover.get("action").getAsString();
            JsonObject legacy = new JsonObject();
            legacy.addProperty("action", action);
            switch (action) {
                case "show_text" -> {
                    if (!hover.has("value")) throw new IllegalArgumentException("Missing hover_event field: value");
                    legacy.add("contents", hover.get("value"));
                }
                case "show_item" -> {
                    JsonObject contents = hover.deepCopy();
                    contents.remove("action");
                    legacy.add("contents", contents);
                }
                case "show_entity" -> {
                    JsonObject contents = hover.deepCopy();
                    contents.remove("action");
                    if (contents.has("id")) contents.add("type", contents.remove("id"));
                    if (contents.has("uuid")) contents.add("id", contents.remove("uuid"));
                    legacy.add("contents", contents);
                }
                default -> throw new IllegalArgumentException("Unsupported hover action: " + action);
            }
            text.add("hoverEvent", legacy);
        }
    }
    //?}
}
