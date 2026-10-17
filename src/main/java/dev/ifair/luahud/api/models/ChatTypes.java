package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaTableValue;
import dev.ifair.luahud.api.LuaType;

/** Shared schemas for modern Minecraft chat text and its nested event payloads. */
public final class ChatTypes {
    private ChatTypes() {}

    public static LuaType<LuaTableValue> text() {
        return LuaType.inputTable("ChatText", (fields, self) -> {
            LuaType<Object> text = LuaType.union(LuaType.string(), self);
            LuaType<Object> argument = LuaType.union(text, LuaType.number(), LuaType.bool());
            fields.optional("text", LuaType.string(), "Literal text")
                    .optional("type", LuaType.union(LuaType.literal("text"), LuaType.literal("translatable"),
                            LuaType.literal("keybind"), LuaType.literal("object")), "Explicit content type")
                    .optional("translate", LuaType.string(), "Translation key")
                    .optional("fallback", LuaType.string(), "Fallback translation format")
                    .optional("with", LuaType.list(argument), "Translation arguments")
                    .optional("keybind", LuaType.string(), "Key binding translation key")
                    .optional("object", LuaType.union(LuaType.literal("atlas"), LuaType.literal("player")), "Object content type; requires Minecraft 1.21.9+")
                    .optional("atlas", LuaType.string(), "Sprite atlas ID; defaults to minecraft:blocks")
                    .optional("sprite", LuaType.string(), "Namespaced sprite ID for atlas content")
                    .optional("player", playerProfile(), "Player profile for player head content")
                    .optional("hat", LuaType.bool(), "Include the player head's hat layer; defaults to true")
                    .optional("extra", LuaType.list(text), "Appended text components, inheriting this component's style")
                    .optional("color", LuaType.string(), "Named color or #RRGGBB")
                    .optional("shadow_color", LuaType.union(LuaType.integer(), LuaType.list(LuaType.number())), "ARGB integer or RGBA float array; requires Minecraft 1.21.2+")
                    .optional("bold", LuaType.bool(), "Bold text")
                    .optional("italic", LuaType.bool(), "Italic text")
                    .optional("underlined", LuaType.bool(), "Underlined text")
                    .optional("strikethrough", LuaType.bool(), "Strikethrough text")
                    .optional("obfuscated", LuaType.bool(), "Randomized glyphs")
                    .optional("font", LuaType.string(), "Namespaced font ID")
                    .optional("insertion", LuaType.string(), "Text inserted into chat on shift-click")
                    .optional("click_event", click(), "Click action and its payload")
                    .optional("hover_event", hover(text), "Hover action and its payload");
        });
    }

    private static LuaType<?> click() {
        return LuaType.alias("ChatClickEvent", LuaType.union(
                LuaType.inputTable("ChatOpenUrl", fields -> fields
                        .field("action", LuaType.literal("open_url"), "Open a URL")
                        .field("url", LuaType.string(), "HTTP or HTTPS URL")),
                LuaType.inputTable("ChatSuggestCommand", fields -> fields
                        .field("action", LuaType.literal("suggest_command"), "Fill the chat input")
                        .field("command", LuaType.string(), "Suggested chat input")),
                LuaType.inputTable("ChatChangePage", fields -> fields
                        .field("action", LuaType.literal("change_page"), "Change a book page")
                        .field("page", LuaType.integer(), "One-based page number")),
                LuaType.inputTable("ChatCopyToClipboard", fields -> fields
                        .field("action", LuaType.literal("copy_to_clipboard"), "Copy text to the clipboard")
                        .field("value", LuaType.string(), "Text to copy"))));
    }

    private static LuaType<?> hover(LuaType<Object> text) {
        return LuaType.alias("ChatHoverEvent", LuaType.union(
                LuaType.inputTable("ChatShowText", fields -> fields
                        .field("action", LuaType.literal("show_text"), "Display a text tooltip")
                        .field("value", text, "Tooltip text component")),
                LuaType.inputTable("ChatShowItem", fields -> fields
                        .field("action", LuaType.literal("show_item"), "Display an item tooltip")
                        .field("id", LuaType.string(), "Namespaced item ID")
                        .optional("count", LuaType.integer(), "Item count; defaults to 1")
                        .optional("components", LuaType.map(LuaType.any()), "Item data components keyed by namespaced ID")),
                LuaType.inputTable("ChatShowEntity", fields -> fields
                        .field("action", LuaType.literal("show_entity"), "Display an entity tooltip")
                        .field("id", LuaType.string(), "Namespaced entity type ID")
                        .field("uuid", LuaType.string(), "Entity UUID")
                        .optional("name", text, "Entity display name"))));
    }

    private static LuaType<?> playerProfile() {
        LuaType<LuaTableValue> property = LuaType.inputTable("ChatProfileProperty", fields -> fields
                .field("name", LuaType.string(), "Profile property name")
                .field("value", LuaType.string(), "Profile property value")
                .optional("signature", LuaType.string(), "Signed property signature"));
        return LuaType.union(LuaType.string(), LuaType.inputTable("ChatPlayerProfile", fields -> fields
                .optional("name", LuaType.string(), "Player name")
                .optional("id", LuaType.string(), "Player UUID")
                .optional("properties", LuaType.list(property), "Player skin profile properties")));
    }
}
