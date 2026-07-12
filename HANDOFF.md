# HANDOFF — AutoJs6 Fleet/Headless Profile System

## Project Overview

**AutoJs6 v6.7.0** (Android automation app, Rhino JavaScript engine).
- Min SDK 24, Compile/Target SDK 36
- Product flavors: `app` (main), `inrt` (non-root runtime)
- Build: Gradle KTS with IDE-aware AGP/Kotlin/KSP auto-detection
- Fork repo: `github.com/djbclark/AutoJs6` (remote: `fork`)
- Upstream repo: `github.com/SuperMonster003/AutoJs6` (remote: `origin`)

## Commit History (most recent first)

| Commit | Description |
|--------|-------------|
| `fa001ad7` | Bump FLEET_BUILD_NUMBER to 5 |
| `bcbf4390` | **Fix** drawer subtitle crash (ArrayIndexOutOfBoundsException bounds check) |
| `e38b5a2a` | Docs: add subtitle crash bug analysis |
| `34e2d8a0` | Bump FLEET_BUILD_NUMBER to 4 |
| `95f0d3f0` | **Feat** broadcast + file output for FleetProfileActivity result |
| `92a39d5` | **Feat** fleet build label in APK names |
| `622ba71` | **Feat** result intent extras for FleetProfileActivity |
| `5668369` | Docs: add HANDOFF.md |
| `453ac8f` | **Fix** human-readable aliases for list preferences |
| `9ad9f2c` | **Fix** console color resources in bottom_sheet_log |
| `b887e79` | **Fix** FleetProfileApplier key prefix + LogBottomSheet AutoJs access |
| `3e955b3` | **Feat** fleet/headless configuration profile support (initial PR #553) |

## Branch: `master` — 5 commits ahead of origin/master

## Key Source Files

| File | Purpose |
|------|---------|
| `app/src/main/java/.../core/pref/fleet/FleetProfileApplier.kt` | Core logic: parses JSON, resolves key/value aliases, writes SharedPreferences |
| `app/src/main/java/.../core/pref/fleet/FleetProfileActivity.kt` | Exported headless Activity — entry point for `am start` |
| `app/src/main/assets/fleet_profile_default.json` | Bundled default profile (watchdog/hands-off deployments) |
| `app/src/main/java/.../ui/main/drawer/DrawerFragment.kt` | Drawer UI — has the subtitle crash bug fix at line 593 |
| `docs/FLEET_PROFILE.md` | User-facing documentation |
| `docs/fleet/SUBTITLE_CRASH_BUG.md` | Analysis of the drawer crash (now fixed) |
| `HANDOFF.md` | This file |
| `version.properties` | Version config — contains `FLEET_BUILD_NUMBER` |
| `app/build.gradle.kts` | Build config — APK naming at lines 780-797 |

## Architecture

### Fleet Profile Flow

1. **FleetProfileActivity** receives intent via `am start` with `-e profile_path` or `-d` URI.
2. Delegates to **FleetProfileApplier.applyFromPath()** or **applyFromUri()**.
3. **applyProfile()** iterates JSON keys, skips `_`-prefixed meta keys:
   - Resolves human-readable key aliases (e.g. `"foreground_service"` → `key_$_foreground_service`) via `aliasToKey` map.
   - Resolves human-readable value aliases for `ListPreference` keys (e.g. `"alarm"` → `key_$_timed_task_backend_alarm`) via `valueAliasToKey` map.
   - Direct raw keys (`key_$_...`) pass through.
4. Writes to `SharedPreferences` via `Pref.get().edit()`.

### Result Delivery (all three fire on every invocation)

1. **Activity result intent** — extras for `startActivityForResult` callers. Extras: `result_success`, `result_applied_count`, `result_skipped_count`, `result_applied_keys[]`, `result_failed_keys[]`, `result_errors[]`, `result_message`.
2. **Broadcast** — action `org.autojs.autojs6.action.FLEET_PROFILE_RESULT` with same extras.
3. **JSON result file** — written to path specified by `-e result_path`, or alongside the profile file (e.g. `/sdcard/Download/autojs6-fleet-result.json`), or fallback `/sdcard/autojs6-fleet-result.json`.

### Key Design Decisions

- **valueAliasToKey** prevents crashes when profiles use short values like `"alarm"` instead of raw internal keys.
- `valueAliasToKey` covers: timed_task_backend, scheduled_restart_backend, restart_strategy, night_mode, keep_screen_on_when_in_foreground, root_mode, hidden_files, file_extensions, documentation_source, app_language, launcher_icon, editor_pinch_to_zoom_strategy, root_record_out_file_type.
- `aliasToKey` covers ~25 common preferences.
- The drawer crash fix (bounds check at `DrawerFragment.kt:593`) prevents `ArrayIndexOutOfBoundsException` when a stored preference key doesn't match the drawer's subtitle keys array.

## Releasing

### APK Naming Convention

```
autojs6-v{version}-stayturgid-{buildType}{buildNumber}-{architecture}.apk
```

Example: `autojs6-v6.7.0-stayturgid-debug5-arm64-v8a.apk`

- `buildType` = `debug` or `release` (auto-detected from variant)
- `buildNumber` = value of `FLEET_BUILD_NUMBER` in `version.properties`
- Architectures: `arm64-v8a`, `armeabi-v7a`, `armeabi`, `x86_64`, `x86`, `universal`

Build number must be bumped before each new build so every binary has a unique name.

### Build & Publish Steps

```bash
# 1. Bump version
#    Edit version.properties: FLEET_BUILD_NUMBER = $((current + 1))

# 2. Build
./gradlew app:assembleAppDebug

# 3. Create GitHub release (not draft)
gh release create v6.7.0-fleet-profile \
    --repo djbclark/AutoJs6 \
    --title "v6.7.0 — Fleet profile build" \
    --notes "Short description of what changed" \
    --target master \
    app/build/outputs/apk/app/debug/autojs6-v6.7.0-stayturgid-debug*-*.apk

# 4. Commit and push
git add -A && git commit -m "chore: bump FLEET_BUILD_NUMBER to N" && git push fork master
```

All releases should be published (not draft). The release tag is always `v6.7.0-fleet-profile` — it's replaced each time (delete + recreate).

## Known Issues

### Fixed: Drawer subtitle crash

**Fixed** in commit `bcbf4390`. See `docs/fleet/SUBTITLE_CRASH_BUG.md`.

`DrawerFragment.refreshSubtitle` at line 590 now checks `i in keys.indices` before array access. When `Pref.keyKeepScreenOnWhenInForeground` returns a value not in `R.array.keys_keep_screen_on_when_in_foreground`, the subtitle shows `null` instead of crashing.

### Unfixed: No test coverage

`FleetProfileApplier` has no unit tests. The `valueAliasToKey` and `aliasToKey` maps are tested only manually via fleet profile deployment.

## External References

- **stayturgid** (`github.com/djbclark/stayturgid`) — fleet orchestration consuming this API. Uses `am start` fire-and-forget with the fleet profile. Also contains a Shizuku catastrophic recovery watchdog with shell-first, UI-last strategy (see `docs/adr/003-shizuku-catastrophic-recovery.md` in the stayturgid repo).

## Potential Next Steps

- Use `FleetProfileApplier` internally at first launch to seed defaults for headless users.
- Accept custom `SharedPreferences` name in `FleetProfileApplier` for testing or multi-profile support.
- Drive `valueAliasToKey` / `aliasToKey` maps from annotation or resource metadata instead of hardcoding.
- Add unit tests for `FleetProfileApplier`.
- The `LogBottomSheet` color resource fix (`9ad9f2c`) is a small UI fix that may need follow-up.
- Merge upstream changes from SuperMonster003/AutoJs6 when available.

## Working Tree

Clean. No stashes. All changes pushed to `djbclark/AutoJs6` master.
