# Bug: `DrawerFragment.onResume` crashes when fleet profile writes a preference value that the drawer subtitle can't render

**Stack trace:**

```
ArrayIndexOutOfBoundsException: length=3; index=-1
  DrawerFragment$onCreate$24.refreshSubtitle(DrawerFragment.kt:590)
  DrawerMenuToggleableItem.sync(DrawerMenuToggleableItem.kt:185)
  DrawerFragment.syncMenuItemStates(DrawerFragment.kt:925)
  DrawerFragment.onResume(DrawerFragment.kt:782)
```

## Root cause

`DrawerFragment.kt` lines 586–599 (`keep_screen_on_when_in_foreground` drawer item):

```kotlin
override fun refreshSubtitle(aimState: Boolean) {
    val oldSubtitle = mKeepScreenOnWhenInForegroundItem.subtitle
    val aimSubtitle = if (ViewUtils.isKeepScreenOnWhenInForegroundDisabled) null else {
        val i = resources.getStringArray(R.array.keys_keep_screen_on_when_in_foreground)
            .indexOf(Pref.keyKeepScreenOnWhenInForeground)
        resources.getStringArray(R.array.values_keep_screen_on_when_in_foreground)[i]  // ← crash: i = -1
    }
    // ...
}
```

When `Pref.keyKeepScreenOnWhenInForeground` returns a value not present in `R.array.keys_keep_screen_on_when_in_foreground`, `indexOf` returns `-1`. The subsequent array access at that index throws `ArrayIndexOutOfBoundsException`.

## Affected drawer items

Every `ListPreference`-type drawer item that renders its current value as a subtitle uses the same `indexOf` → array-access pattern in its `refreshSubtitle` override. All are susceptible:

- keep_screen_on_when_in_foreground
- night_mode
- restart_strategy
- theme
- (any other ListPreference with subtitle rendering)

Boolean toggles (foreground_service, guard_mode, stable_mode, etc.) are **not** affected — only items displaying a selected value as text.

## How fleet profile triggers it

`FleetProfileApplier` writes preference values via `valueAliasToKey` resolution. If the resolved internal key doesn't match an entry in the drawer's `R.array.keys_*` array, `indexOf` returns `-1` and the drawer crashes on next `onResume`.

The `keep_screen_on_when_in_foreground` mapping `"off"` → `key_$_keep_screen_on_when_in_foreground_disabled` IS in the keys array at index 0, so that specific item normally works. But the crash could come from **any** list-type preference where the stored key doesn't match.

## Possible fixes

### Option A (defensive — in `DrawerFragment.kt`)

Add a bounds check before every `refreshSubtitle` array access:

```kotlin
val i = resources.getStringArray(R.array.keys_keep_screen_on_when_in_foreground)
    .indexOf(Pref.keyKeepScreenOnWhenInForeground)
val subtitle = if (i in 0 until resources.getStringArray(R.array.values_keep_screen_on_when_in_foreground).size) {
    resources.getStringArray(R.array.values_keep_screen_on_when_in_foreground)[i]
} else {
    null  // fallback: no subtitle shown
}
```

This should be applied to **all** `refreshSubtitle` overrides in `DrawerFragment.kt` that use this pattern.

### Option B (in `FleetProfileActivity.kt`)

After applying the profile, clear drawer-subtitle SharedPreferences keys so the drawer falls back to defaults:

```kotlin
Pref.keyKeepScreenOnWhenInForeground.remove()
// ... repeat for every drawer subtitle key
```

This is fragile (must be kept in sync with the drawer) and doesn't prevent the crash if some other code path writes an unrecognized value.

### Recommendation

**Option A** is the correct fix — it's defensive, comprehensive, and protects against any caller writing an unrecognized value to SharedPreferences.
