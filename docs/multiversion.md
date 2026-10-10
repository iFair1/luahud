# Multiversion development

## Current status

The build uses Stonecutter 0.9.8 and a single source tree. **Only Minecraft 1.21
is enabled and supported.** This change establishes the build infrastructure;
it does not port the mod to later 1.21.x or 26.x releases.

No Git branch per Minecraft version is needed. Features and fixes are developed
in the root `src/` tree against 1.21. Stonecutter generates sources for the other
targets when they are added. Never edit generated `versions/*/src/` directories.

## Everyday commands

| Command | Target |
| --- | --- |
| `./gradlew build` | Build and test 1.21 |
| `./gradlew test` | Test 1.21 |
| `./gradlew runClient` | Launch 1.21 |
| `./gradlew generateApiDocs` | Export the 1.21 API to `build/generated/api/api.json` |
| `./gradlew clean` | Clean 1.21 and root build outputs |
| `./gradlew buildAll` | Build and test every enabled target |
| `./gradlew cleanAll` | Clean every enabled target and root build outputs |

The root shortcuts always select 1.21. Use an explicit project path to work on
another target, for example `./gradlew :1.21.1:build` or
`./gradlew :1.21.1:runClient` **after enabling that target**.

Each target produces separate artifacts under `versions/<version>/build/libs/`.
Mod versions include `+mc<version>` to distinguish artifacts, including Maven
publications. `fabric.mod.json` receives the Minecraft, Java and loader
requirements from Gradle properties; do not broaden compatibility without
checking the corresponding releases.

## Adding a 1.21.x target

1. Add its exact Minecraft version to `versions(...)` in `settings.gradle`.
   Keep `vcsVersion = '1.21'`.
2. Copy `versions/1.21/gradle.properties` into the new version directory. Set
   `minecraft_version`, `minecraft_range`, `yarn_mappings`, `fabric_api_version`,
   `modmenu_version` and `java_version` for that release. Start with an exact
   `minecraft_range` matching the target.
3. Compile with the explicit target task. Adapt changed Minecraft APIs with
   Stonecutter conditions. Keep rendering, key binding and mixin differences
   close to their Minecraft integration points.
4. Run `./gradlew buildAll generateApiDocs` and launch the new target using
   its `runClient` task. Check profile loading/reloading, HUD text/items/entities,
   configuration screens, key bindings and jukebox playback in-game. Compilation
   alone does not validate rendering or mixin injection at runtime.
5. Document the newly supported target. CI already builds all enabled projects
   and uploads their jars.

Small differences can use Stonecutter's Java comment syntax:

```java
//? if <=1.21 {
baselineCall();
//?} else {
/*newerCall();
*///?}
```

For substantial differences, extract a focused compatibility class (for
example, HUD drawing) and keep the conditions inside it. Keep the Lua engine,
layout calculations and addon-facing API shared. Avoid spreading version
conditions through unrelated runtime code or copying the whole mod per version.

When temporarily switching the editable source version, use the
`stonecutterSwitchTo...` tasks listed by `./gradlew tasks --all`. Switch back to
1.21 before committing; `stonecutter.gradle` must retain 1.21 as its active
version. The root command aliases remain pinned to 1.21 during a switch.

## Preparing the 26.x port

26.1 introduces two additional changes: Java 25 and unobfuscated Minecraft
using Mojang's names. The current source uses Yarn names. Stonecutter handles
version conditions; it does not automatically translate Minecraft APIs.

Before enabling 26.x:

1. Migrate the shared 1.21 source and tests from Yarn to official Mojang mappings
   using Loom's mapping migration workflow. Review string references in mixins
   and bytecode tests as well as Java imports. Verify 1.21 again before porting.
2. Split the build setup where necessary: older targets use
   `net.fabricmc.fabric-loom-remap` plus official Mojang mappings, whereas 26.x
   uses `net.fabricmc.fabric-loom` without a mappings dependency. Use distinct
   central build scripts if plugin selection requires it, sharing common build
   configuration. Do not merely change `minecraft_version` in this Yarn build.
3. Run Gradle with JDK 25 and configure Java toolchains for the enabled targets
   as needed. Set the 26.x Java requirement to 25 and update the mixin
   compatibility level for that target. The current CI already runs JDK 25.
4. Port rendering, GUI, key bindings and sound tracking against the actual new
   APIs. Preserve the Lua API behavior and validate each target in-game.

These steps let 1.21 remain the development baseline even when newer targets
need another Java version and different Minecraft integration code. New targets
should only enter the supported matrix once their implementation works.

References:

- [Stonecutter source and usage](https://github.com/stonecutter-versioning/stonecutter/tree/0.9)
- [Fabric: Minecraft 26.1 changes](https://www.fabricmc.net/2026/03/14/261.html)
- [Fabric mapping migration](https://docs.fabricmc.net/develop/porting/mappings/index)
