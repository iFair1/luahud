# LuaHUD
An extremely customizable and powerful Minecraft mod which allows you to draw HUDs using Lua scripting.

Visit the [website](https://luahud.ifair.dev) for  more information.

[Download the mod on Modrinth](https://modrinth.com/mod/luahud)

## Development
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
the filename, for example `luahud-0.1.0+mc1.21.jar`.

## License

LuaHUD is licensed under the Lesser GNU General Public License v3. See [LICENSE](LICENSE).
