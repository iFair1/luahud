# LuaHUD

## Commands

- `/luahud` or `/luahud menu` opens the main menu.
- `/luahud errors` opens the error browser. `/luahud errors <entry>` selects a specific error.
- `/luahud reload` reloads active profiles and Lua modules, then runs `init` again.
- `/luahud report system <phase> <message>` creates a system error.
- `/luahud report profile <profile> <phase> <message>` creates a profile error.
  Quote profile names or phases containing spaces. The message consumes the
  rest of the command. Errors use the usual browser, notifications and duplicate
  counting; profile errors are cleared on reload.

```text
/luahud report system "config read" Test system error
/luahud report profile "My Profile" tick My Profile.lua:12: Test profile error
```

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

## Models

Define each field once and use its typed handle in the decoder. Fields are
output-only by default. Input presence (`inputRequired`, `inputOptional`, or
`inputDefault`) is independent of output nullability (`optionalInteger`, etc.).
Use `type()` for returned values and `inputType()` for function arguments.

```java
record Counter(String label, Integer count) {}

ModelBuilder<Counter> counter = api.model("Counter", Counter.class);
var label = counter.string("label", "Display label", Counter::label)
    .inputRequired();
var count = counter.integer("count", "Count; defaults to 1", Counter::count)
    .inputDefault(1);
counter.fromFields(fields -> new Counter(fields.get(label), fields.get(count)));

var echo = api.root().function("echoCounter", counter.type(), "Echo a counter");
var argument = echo.parameter("counter", counter.inputType(), "Counter or input table");
echo.call(arguments -> arguments.get(argument));
```

The generated stub describes `Counter` and `CounterInput` separately, using the
same field definitions. Plain tables accept only input-capable fields; unknown
and output-only fields produce a Lua error. A decoder cannot read an unrelated
model's field handle or an output-only field.

Model proxies keep lazy access to Java values. An unchanged proxy passed to a
function returns its original Java object. When Lua overrides input fields,
the decoder receives explicit values first and lazily resolves the remaining
input fields from the source. `fields.isExplicit(field)` distinguishes overrides
from source values and defaults. `fields.source()` returns the original Java
object, or null for plain tables, allowing a decoder to preserve native state.
Assignments affect the Lua proxy; they do not directly mutate the Java object.
Writing an output-only field and passing that proxy to a function is rejected.

Models without a decoder accept their own unchanged proxies as arguments but
do not accept plain tables. For example, `PlacedBlock` and `Horse` expose output
properties, while `EntityInput` resolves a loaded entity using only `uuid`.
`ItemStackInput` requires only `id`; `count` defaults to 1. Its optional inputs
are `damage`, `damageLeft`, and `enchantments`. Explicit `damage` wins over
`damageLeft`; either override wins over lazy source durability. Stack overrides
copy the source stack and preserve unrelated components.

`StatusEffectInput` requires `id`, defaults `level` to 1, and accepts
`durationInTicks` or `durationInSeconds` (default duration: 0). Explicit ticks
win over explicit seconds; either wins over source duration. Both duration
outputs preserve -1 for infinite effects.

### Horse-family properties

`lh.player.horse` is the currently ridden horse-family animal's `Horse` model,
or nil when the player is not riding one. `Entity.horse` exposes the same model
for horse-family entities, including horses, donkeys, mules, skeleton/zombie
horses, llamas, and camels. Other entities have nil in this field.

`Horse` has `movementSpeed`, `jumpStrength`, `isSaddled`, `isTamed`, `hasChest`,
and optional `rider`. Speed and jump strength are effective, client-synchronized
attribute values, rather than blocks per second or jump height. Saddle, tame,
and chest values come from tracked entity state. `rider` is the first passenger
as an `Entity`, including passengers that are not players, and is nil when
unoccupied. `hasChest` is false for species that cannot carry a chest.

```lua
lh.on("tick", function()
    local horse = lh.player.horse
    if horse then
        lh.drawln("Speed: " .. horse.movementSpeed)
        lh.drawln("Jump: " .. horse.jumpStrength)
        if horse.rider then
            lh.drawln("Rider: " .. horse.rider.name)
        end
    end
end)
```

Migration from the previous model builder: declare the model separately, keep
field handles instead of chaining field declarations, and replace `fromTable`
string lookups with `fromFields` and `fields.get(handle)`. Mark accepted inputs
explicitly and change function parameters to `inputType()`. Custom construction
and registry resolution remain in the decoder; no additional DTO is required
when exposing an existing Java class.

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
