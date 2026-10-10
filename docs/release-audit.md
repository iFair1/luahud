# Release audit — 2026-10-10

This pass reviewed the multiversion build, Minecraft integration, profile
configuration and file watching, Lua execution, and rendering lifecycle.
The changes are in the root source tree; Stonecutter remains on 1.21.

## Fixed in this pass

| Finding | Trigger and impact | Change |
| --- | --- | --- |
| Profile paths could escape their directory | Creating or renaming a profile to `../Outside` could write or move `config/Outside.lua`. Blank names also produced profiles removed on the next config load. | Validate names before mutation and when loading JSON. Reject blank names, path separators, absolute paths, and invalid filesystem paths. |
| Invalid bindings could crash the config screen | A hand-edited binding such as `key.keyboard.invalid` reached `ProfileEntry.getKeybindText()` without validation. Out-of-range numeric bindings could also reach GLFW polling. | Normalize invalid bindings to the unbound key when loading, adding, or changing profiles; persist load-time normalization. |
| Editor saves could reset profile settings | A delete event consumed before the corresponding recreate event removed the profile; recreation appended a new active profile with default visibility and binding. | Reconcile the final filesystem state after 150 ms of quiet, preserving settings and order across short save bursts. This also reduces reloads of partially written files. |
| Lua-suffixed directories became profiles | A directory named `Directory.lua` was registered during startup and subsequently failed to load as a script. | Register regular files only. |
| Duplicate-name suffixes could overflow | A collision with `Profile (2147483647)` wrapped negative; larger suffixes threw `NumberFormatException`. | Increment suffixes with `BigInteger`. |
| Watch event overflow was discarded | Lost events could leave the profile list and runtime stale. | Resynchronize top-level profiles and request reload after overflow. |
| README contradicted release metadata | The README claimed CC0 although `LICENSE` and mod metadata specify GPLv3, and described sound timing as tick-based. | Correct both descriptions. |

The watcher is registered before startup synchronization, closing the previous
gap where edits between scanning and registration could be missed. Each watcher
thread also retains its own service, and stopping an interrupted watcher does
not create a system error.

## Further findings and improvement opportunities

These are source-review findings; no live Minecraft reproduction was performed
for them in this pass.

- **Failed Lua loads can retain callbacks (medium priority).** In
  `CobaltRuntime.execute`, registrations are removed on timeout, but not on an
  ordinary Lua error. For example, a profile can register an `init` or `tick`
  callback and then call `error('load failed')`; `Engine` reports a load error
  but still fires the registered callback. Consider rolling back registrations
  created by a failed invocation, preserving earlier successful registrations.
- **The entity lookup cache can retain a disconnected world (medium priority).**
  `RenderModule.findEntity(String)` stores a `ClientWorld` in a static field.
  Leaving a world resets sections and the runtime, but does not clear that
  field. After drawing an entity by UUID, the old world can stay reachable until
  another UUID lookup occurs in a different world. Add explicit cache cleanup
  at world exit and shutdown, separate from per-callback section resets.
- **Duplicate profile names in JSON remain ambiguous (medium priority).**
  Loading JSON does not deduplicate names. The engine executes both entries,
  while configuration mutations find only the first entry. Add a deterministic
  normalization policy, ideally retaining the first entry and reporting the
  duplicate.
- **Nested modules need manual reload (low priority; already documented).**
  The file watcher observes the top-level script directory only. Recursive
  watching could improve the workflow for `require('helpers.colors')`; nested
  files should trigger reload without becoming standalone profiles.
- **Save debouncing has a finite window.** A delete/recreate save interrupted
  for longer than 150 ms can still be interpreted as a real deletion. Full
  preservation would require retaining missing-profile settings or a longer
  deletion grace period; neither policy is introduced here.

## Validation

- `./gradlew test` passed on 1.21.
- `./gradlew buildAll` passed for all 12 targets, 1.21 through 1.21.11.
  Each target ran 162 tests, with zero failures, errors, or skipped tests.
  This includes 26 added regression cases for names, bindings, suffixes, and
  actual WatchService creation/deletion/save behavior.
- Inspected each remapped release jar: exact Minecraft dependency and version
  suffix, bundled Cobalt, parseable mixin configuration, and entity-lighting
  mixins enabled only for 1.21.6–1.21.8.
- Existing tests load sound and lighting mixin targets and inspect the GPU
  measurement hook. They do not exercise a real OpenGL rendering session.
- `git diff --check` passed.

Before release, run in-game checks on each advertised target. Focus on HUD
text/items/textures/entities at different GUI scales, multiple entity icons,
F1 and F3, profile editing and bindings, world/dimension transitions, and
jukebox pause/resume. The rendering boundaries at 1.21.2, 1.21.5, 1.21.6,
1.21.9, and 1.21.11 deserve particular attention. Live client rendering and
audio playback were not tested during this audit.
