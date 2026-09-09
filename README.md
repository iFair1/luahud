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

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
