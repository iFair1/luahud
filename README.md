# LuaHUD

## Addon API

Addons can contribute engine-neutral values and synchronous functions through the
`luahud:api` Fabric entrypoint. Cobalt is an internal implementation detail and
must not be used by addons.

```json
"entrypoints": {
  "luahud:api": ["com.example.ExampleApi"]
}
```

```java
public final class ExampleApi implements LuaHudApiEntrypoint {
    @Override
    public void register(LuaApiRegistrar api) {
        api.root().namespace("example", namespace -> namespace
            .string("label", "Current label", this::getLabel)
            .integer("count", "Current count", this::getCount));
    }
}
```

Register during this entrypoint only. Definitions are frozen after addon discovery;
provider failures are isolated and conflicting paths use the first definition.

## Profile lifecycle

Lua profiles run only while a world and player are loaded. Active profiles are
loaded on the first in-world client tick, then `init` runs before timers and
`tick` callbacks. Reloading profiles in-world also fires `init`. Leaving a world
discards the Lua runtime and HUD contents; joining again loads fresh profiles.
Changes made from the main menu take effect on the next world entry.

## Lua modules

`require("utils")` loads `config/luahud/utils.lua`, falling back to
`config/luahud/utils/init.lua`. Dotted names such as `require("helpers.colors")`
resolve to `helpers/colors.lua` or `helpers/colors/init.lua` in that directory.
Module files do not need to be active profiles; leave them inactive to avoid
also running them as standalone profiles.

```lua
-- config/luahud/utils.lua
local utils = {}
function utils.greeting() return "Hello, World!" end
return utils

-- In an active profile:
local utils = require("utils")
lh.on("tick", function() lh.drawln(utils.greeting()) end)
```

Modules share the runtime and are cached in `package.loaded` across profiles.
Reloading the HUD clears this cache. Module loading follows Lua 5.2 semantics:
`package.searchers` (rather than 5.1's `package.loaders`), `package.searchpath`,
`package.preload`, `package.path`, `package.cpath`, and `package.config` are
available. Loaders receive the module name and the searcher's second result
(the filename for Lua files). Failed loads can be retried; there is no implicit
cycle sentinel. Circular dependencies must populate `package.loaded` themselves.

Path patterns are relative to `config/luahud` and cannot load files outside it.
The JVM runtime does not load native C libraries: `package.cpath` defaults to
an empty string and `package.loadlib` returns `nil`, an error message, and
`"absent"`, as in Lua 5.2 builds without dynamic library support. These are
host restrictions; this does not make the entire Cobalt interpreter Lua 5.2.
Top-level Lua edits trigger automatic reloads; after editing a module in a
subdirectory, reload the HUD manually.

## Lua execution timeout

LuaHUD stops a profile invocation when it runs for more than 25 milliseconds so
an infinite Lua loop cannot permanently freeze the client. The limit applies to
profile loading, event listeners, and interval callbacks. It can be changed in
`config/luahud.json`:

```json
"luaTimeoutMs": 25
```

Values from 1 through 1000 are accepted. Set the value to 0 to disable the
watchdog completely.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
