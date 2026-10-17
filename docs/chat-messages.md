# Local chat messages

`lh.sendMessage(message)` adds a message to the local chat history. It returns
no value and never sends a player chat message or command to the server.
Call it from a reload, tick, or other occasional callback; calling it on every
render will add a message every frame.

Strings become literal Minecraft text. They are never parsed as JSON and LuaHUD
does not parse or replace `§` formatting codes.

```lua
lh.sendMessage("Profil yüklendi.")
```

Tables represent Minecraft text components, using modern field names on every
supported Minecraft version. Common fields include `text`, `translate`, `fallback`,
`with`, `keybind`, `extra`, `color` (a named color or `#RRGGBB`), `bold`, `italic`,
`underlined`, `strikethrough`, `obfuscated`, `font`, and `insertion`.
Nested components and argument lists use ordinary Lua tables and arrays.
Minecraft's text codec validates component contents.

```lua
lh.sendMessage({
    text = "[LuaHUD] ",
    color = "gold",
    extra = {
        { text = "Profil yüklendi. ", color = "green" },
        {
            text = "Kopyala",
            underlined = true,
            click_event = { action = "copy_to_clipboard", value = "örnek" },
            hover_event = {
                action = "show_text",
                value = { text = "Metni panoya kopyala", color = "gray" }
            }
        }
    }
})
```

Use `click_event` and `hover_event`, including inside `extra`, `with`, and hover
text. Legacy `clickEvent` and `hoverEvent` names are rejected. Click payloads use
`url` for `open_url`, `command` for `suggest_command`, `page` for
`change_page`, and `value` for `copy_to_clipboard`. The `show_text` hover action
uses `value`; `show_item` uses `id`, optional `count` and `components`; `show_entity`
uses entity type `id`, entity `uuid`, and optional text `name`.

LuaHUD adapts these event fields internally for versions before 1.21.5. Features
that do not exist in the running Minecraft version cannot be used there (for
example, `shadow_color` requires 1.21.2 or newer). Only local content and click
actions are exposed. Scoreboard, selector, and NBT content and their associated
fields are rejected, including inside nested text. The `run_command`, `custom`,
and `show_dialog` click actions are not exposed. `suggest_command` only fills the
local chat input; sending it still requires the user's normal chat submission.
