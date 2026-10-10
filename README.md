# LuaHUD

## Development versions

Minecraft **1.21** is the development baseline. Stonecutter shares the root
`src/` tree between version projects; version-specific dependencies live in
`versions/<minecraft>/gradle.properties`. Targets cover every stable release from
1.21 through 1.21.11; 26.x is not enabled.

```bash
./gradlew build            # Build and test 1.21
./gradlew test             # Test 1.21
./gradlew runClient        # Launch the 1.21 development client
./gradlew buildAll         # Build and test all enabled targets (also used by CI)
./gradlew cleanAll         # Clean outputs for all enabled targets
```

Jars are written to `versions/1.21/build/libs/`, with the Minecraft version in
the filename, for example `luahud-1.0.0+mc1.21.jar`. API documentation remains at
`build/generated/api/api.json`. See [the multiversion guide](docs/multiversion.md)
for target commands, compatibility code and the planned 26.x workflow.

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

## Sound API

`lh.sound.mood` is the ambient mood percentage (0–100), matching the F3 display.
`lh.sound.record` is `nil` when no jukebox disc is playing within 64 blocks.
If several discs are playing, it returns the nearest one:

- `id`: namespaced disc item ID, such as `minecraft:music_disc_cat` (falls back to
  the song ID for songs without a matching item).
- `name`: localized song name.
- `length`, `elapsed`, `remaining`: total, elapsed, and remaining time in seconds.
  Playback time advances with client ticks and pauses with the game.

```lua
local record = lh.sound.record
if record then
    lh.drawln(record.name .. " — " .. math.ceil(record.remaining) .. "s")
end
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

## Generated API reference

Run `./gradlew generateApiDocs` to export the built-in Lua API to
`build/generated/api/api.json` for the documentation site. The task runs without
launching Minecraft or discovering user addons. Copy the JSON into the site's
data directory to generate searchable property, function, and model pages;
write guides separately in Markdown.

The JSON contains `schemaVersion`, `root`, `namespaces`, `properties`,
`functions`, and `types`. Entries include descriptions and provider IDs;
functions include ordered parameters and return types. Type expressions include
display strings, nested element/union types, and references to named models.
Model fields describe output nullability and input presence separately.
Descriptions and type names share the same registry as the Lua stub.

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

Models are table schemas on the Lua side; scripts do not need constructors or
class instances. The generated stub keeps names such as `CounterInput` for
input schemas, using the same field definitions as the returned `Counter` view.
A function consumes only its schema's input-capable fields. Additional fields,
including output-only fields, are ignored. Required fields and the types of
consumed fields are still validated. Ordinary Lua lookup (`__index`) is supported,
so namespaces and other model views can satisfy a schema too. A decoder cannot
read an unrelated model's field handle or an output-only field.

Options defined with `LuaType.inputTable`, including drawing and section options,
are strict: undeclared keys produce a Lua error, helping catch misspelled options.

Model proxies keep lazy access to Java values. An unchanged proxy passed to a
function returns its original Java object. When Lua overrides input fields,
the decoder receives explicit values first and lazily resolves the remaining
input fields from the source. `fields.isExplicit(field)` distinguishes overrides
from source values and defaults. `fields.source()` returns the original Java
object, or null for plain tables, allowing a decoder to preserve native state.
Assignments affect the Lua proxy; they do not directly mutate the Java object.
Output-only and additional fields on a proxy are ignored when it is passed to a
function; they do not change the source Java object.

Models without a decoder accept their own unchanged proxies as arguments but
do not accept plain tables. For example, `PlacedBlock` and `Horse` expose output
properties. Entity arguments accept any table with a string `uuid` field and
resolve an already loaded entity; other fields do not configure that entity.
For example, all of these calls identify the same player:

```lua
lh.drawEntity(lh.player)
lh.drawEntity({ uuid = lh.player.uuid })
lh.drawEntity(lh.player.uuid)
```

Item stack arguments require only `id`; `count` defaults to 1. Their optional inputs
are `damage`, `damageLeft`, and `enchantments`. Explicit `damage` wins over
`damageLeft`; either override wins over lazy source durability. Stack overrides
copy the source stack and preserve unrelated components.

Status effect arguments require `id`, default `level` to 1, and accept
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
