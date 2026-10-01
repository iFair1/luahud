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
