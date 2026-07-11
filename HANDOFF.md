# HANDOFF — AutoJs6 Fleet/Headless Profile System

## Project Overview

**AutoJs6 v6.7.0** (Android automation app, Rhino JavaScript engine).
- Min SDK 24, Compile/Target SDK 36
- Product flavors: `app` (main), `inrt` (non-root runtime)
- Build: Gradle KTS with IDE-aware AGP/Kotlin/KSP auto-detection

## Where We Left Off

We implemented and fixed the **fleet/headless configuration profile system** (Issue #553). Four commits on `master`:

| Commit | Description |
|--------|-------------|
| `3e955b3` | **feat:** fleet/headless configuration profile support (initial PR) |
| `b887e79` | **fix:** correct FleetProfileApplier key prefix + LogBottomSheet AutoJs access |
| `9ad9f2c` | **fix:** reference existing console color resources in bottom_sheet_log |
| `453ac8f` | **fix:** map human-readable aliases to internal keys for list preferences |

Working tree is clean, no stashes.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/org/autojs/autojs/core/pref/fleet/FleetProfileApplier.kt` | Core logic — parses JSON, resolves key/value aliases, writes SharedPreferences |
| `app/src/main/java/org/autojs/autojs/core/pref/fleet/FleetProfileActivity.kt` | Exported headless Activity entry point (invoked via `am start`) |
| `app/src/main/assets/fleet_profile_default.json` | Bundled default profile (watchdog/hands-off deployments) |
| `docs/FLEET_PROFILE.md` | User-facing documentation |

## Architecture

1. **FleetProfileActivity** receives intent via `am start` with `-e profile_path` or `-d` URI.
2. Delegates to **FleetProfileApplier.applyFromPath()** or **applyFromUri()**.
3. **applyProfile()** iterates JSON keys, skips `_`-prefixed meta keys:
   - Resolves human-readable key aliases (e.g. `"foreground_service"` → `key_$_foreground_service`) via `aliasToKey` map.
   - Resolves human-readable value aliases for `ListPreference` keys (e.g. `"alarm"` → `key_$_timed_task_backend_alarm`) via `valueAliasToKey` map.
   - Direct raw keys (`key_$_...`) pass through.
4. Writes to `SharedPreferences` via `Pref.get().edit()`.

### Key Design Decisions

- **valueAliasToKey** was the final fix — prevents crashes when profiles use human-readble short values like `"alarm"` instead of raw internal keys.
- `valueAliasToKey` covers: timed_task_backend, scheduled_restart_backend, restart_strategy, night_mode, keep_screen_on_when_in_foreground, root_mode, hidden_files, file_extensions, documentation_source, app_language, launcher_icon, editor_pinch_to_zoom_strategy, root_record_out_file_type.
- `aliasToKey` covers ~25 common preferences.

## External References

- **stayturgid** ([github.com/djbclark/stayturgid](https://github.com/djbclark/stayturgid)) — example fleet orchestration that consumes this API. May need updates if the API changes.

## Building APKs

Build a debug APK:

```bash
./gradlew app:assembleAppDebug
```

APKs are output to `app/build/outputs/apk/app/debug/` with naming convention:

```
autojs6-v{version}-stayturgid-{buildType}{buildNumber}-{architecture}.apk
```

Example: `autojs6-v6.7.0-stayturgid-debug3-arm64-v8a.apk`

- `buildType` = `debug` or `release` (auto-detected from variant)
- `buildNumber` = value of `FLEET_BUILD_NUMBER` in `version.properties` (increment this before each fleet build)
- Architectures: `arm64-v8a`, `armeabi-v7a`, `armeabi`, `x86_64`, `x86`, `universal`

To publish a new build:

1. Bump `FLEET_BUILD_NUMBER` in `version.properties`
2. Run `./gradlew app:assembleAppDebug`
3. Upload APKs to a GitHub release
4. Push the version.properties commit

## Known Bug — Drawer subtitle crash (unfixed)

See `docs/fleet/SUBTITLE_CRASH_BUG.md` for full analysis.

When `FleetProfileActivity` writes a `ListPreference` value whose internal key doesn't match the drawer's `R.array.keys_*` array, `DrawerFragment.onResume` crashes with `ArrayIndexOutOfBoundsException`. The fix should apply bounds checks in all `refreshSubtitle` overrides in `DrawerFragment.kt`.

This is the highest-priority unfixed issue.

## Potential Next Steps

- Use `FleetProfileApplier` internally at first launch (to seed defaults for headless users).
- `FleetProfileApplier` currently only writes to the default `SharedPreferences` — could accept a custom `SharedPreferences` name for testing or multi-profile support.
- The `valueAliasToKey` / `aliasToKey` maps are hardcoded — could be driven from annotation or resource metadata.
- Tests — no test coverage exists for `FleetProfileApplier`.
- The `LogBottomSheet` color resource fix (`9ad9f2c`) is a small UI bug that may have follow-up work.
