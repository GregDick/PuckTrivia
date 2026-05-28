# Material You / Dynamic Color — Android Developer Reference

> **Version Documented**: Material3 Compose (androidx.compose.material3 1.x), Android 12–14 (API 31–34)
> **Last Updated**: 2026-05-27
> **Primary sources**: [developer.android.com](https://developer.android.com/develop/ui/compose/designsystems/material3), [m3.material.io](https://m3.material.io/styles/color/roles), [source.android.com](https://source.android.com/docs/core/display/dynamic-color), [Android Developers Blog](https://android-developers.googleblog.com/2022/05/implementing-dynamic-color-lessons-from.html)

---

## 1. How It Works Under the Hood

### Wallpaper extraction pipeline

1. **Seed color extraction** — `com.android.systemui.monet.ColorScheme#getSeedColors` samples the wallpaper and picks one or more candidate seed colors. If none pass quality thresholds, the system falls back to `0xFF1B6EF3` (a Google-blue default).
2. **Key color derivation** — The seed is fanned out into five key colors: Primary, Secondary, Tertiary, Neutral, Neutral Variant. These are not the five accent palettes themselves; they are the *inputs* to the palette algorithm.
3. **Tonal palette generation** — Each key color becomes a 13-stop tonal palette (`system_accent1`/`2`/`3`, `system_neutral1`/`2`). The 13 tones are indexed: 0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000. Hue and chroma are undefined at build time — they are runtime values.
4. **Color scheme derivation** — Each role in the `ColorScheme` is mapped to a specific tone from one of the five palettes. The mapping is fixed by the Material spec; only the palette hues vary.

### Theme style variants (Android 12+/13+)

The palette algorithm has named variants settable via `Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES`:

| Style | Android | Vibe |
|---|---|---|
| `TONAL_SPOT` | 12+ (default) | Mid-vibrancy, analogous accents |
| `VIBRANT` | 13+ | High-vibrancy, harmonious shifts |
| `EXPRESSIVE` | 13+ | High-vibrancy, unexpected accents |
| `SPRITZ` | 13+ | Low-vibrancy, soft wash |
| `RAINBOW` | 13+ | Chromatic accents (not intended for wallpaper extraction) |
| `FRUIT_SALAD` | 13+ | Two-tone expression (not intended for wallpaper extraction) |

Apps see the same `ColorScheme` token names regardless of which variant the user chose.

### Compose APIs

```kotlin
// In your Theme composable:
val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S  // API 31

val colorScheme = when {
    dynamicColor && darkTheme  -> dynamicDarkColorScheme(LocalContext.current)
    dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
    darkTheme                  -> DarkColorScheme   // your static fallback
    else                       -> LightColorScheme  // your static fallback
}

MaterialTheme(colorScheme = colorScheme, ...) { ... }
```

Both functions are top-level Kotlin functions in `androidx.compose.material3`. They take a `Context` to read the current `WallpaperColors` from the system. **There is no equivalent in the Views-based `DynamicColors.applyToActivitiesIfAvailable()` path for Compose** — use the functions above directly.

### When values refresh

- Wallpaper change triggers a **system-wide process restart** for running apps (treated as a configuration change too low-level to handle in-process). There is no `COLOR_CHANGED_ACTION` broadcast you need to handle — the activity/process restart is mandatory and cannot be opted out of.
- User switching the style variant in **Wallpaper & style** settings also triggers a restart.
- Compose recomposes automatically on the next resume because `dynamicDarkColorScheme(context)` reads live system state.

### Pre-API 31 fallback

`dynamicDarkColorScheme` / `dynamicLightColorScheme` **do not exist below API 31**. You must guard with the `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` check (as shown above) and supply your own `lightColorScheme(...)` / `darkColorScheme(...)` for those devices. There is no partial dynamic color on API 30.

Because your `minSdk = 30`, **every user on Android 11 gets your static fallback** — plan your fallback palette with the same care as your dynamic path.

---

## 2. What Slots Get Filled In

### Colors only — not typography or shapes

Dynamic color fills **every slot in the `ColorScheme`**. Typography (`MaterialTheme.typography`) and shapes (`MaterialTheme.shapes`) are completely unaffected.

### Complete `ColorScheme` slot list (Compose Material3)

All ~29 named color properties are populated. Grouped by role family:

**Accent groups (Primary / Secondary / Tertiary — same structure for each):**
- `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `inversePrimary`
- `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`
- `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`
- `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` *(and secondary/tertiary equivalents)*

**Error:**
- `error`, `onError`, `errorContainer`, `onErrorContainer`

> **Critical note on error color**: Dynamic color does **not** preserve any semantic meaning for error — it derives `error` from the palette like every other role. On some wallpapers the generated `error` color can look similar to `primary` (e.g., both warm/red tones). If your UI relies on a distinctive red for error states, you must override this slot (see Section 6).

**Surface family:**
- `background`, `onBackground`
- `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `surfaceTint`
- `inverseSurface`, `inverseOnSurface`
- `surfaceBright`, `surfaceDim`
- `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerLowest`, `surfaceContainerHigh`, `surfaceContainerHighest`

**Utility:**
- `outline`, `outlineVariant`, `scrim`

---

## 3. Accessibility / Contrast Guarantees

### The official claim

The Material 3 color system documentation states:

> "Color roles support accessibility: The color system is built on more accessible color pairings. **These color pairs provide a minimum of 3:1 color contrast.**"

Source: [Color roles and tokens — Wear Android Developers](https://developer.android.com/design/ui/wear/guides/styles/color/roles-tokens) (same spec as phone M3).

**This is a 3:1 guarantee, not WCAG AA (4.5:1).** WCAG AA requires 4.5:1 for normal text and 3:1 for large text (18sp+ or 14sp+ bold). Material 3's floor matches WCAG AA for *large text only*.

### What the guarantee covers

The 3:1 guarantee applies when you use the **intended pairs**: `onX` on top of `X`, and `onXContainer` on top of `XContainer`. Examples:
- `onPrimary` on `primary`
- `onPrimaryContainer` on `primaryContainer`
- `onSurface` / `onSurfaceVariant` on `surface`, `surfaceContainer*`
- `onError` on `error`, `onErrorContainer` on `errorContainer`

**Mixing non-paired roles breaks the guarantee.** E.g., `primary` text on `surfaceContainer` has no documented contrast floor.

### Practical limitation for normal-text UI

If your app has normal-weight text at 16sp (a common body size), WCAG AA requires 4.5:1. The Material 3 spec does not guarantee this. The Chrome team noted the tonal system enforces "a minimum 60 luminance spread" which typically exceeds 4.5:1 in practice for many palettes — but this is not an explicit specification, and it can fail for desaturated wallpapers (e.g., `SPRITZ` style).

### High-contrast mode interaction

When a user enables the **High Contrast Text** accessibility option in Android settings, Material You dynamic color is suppressed on some devices/OEMs — the system reverts to non-dynamic high-contrast overrides. This is a known platform behavior, not a bug you can fix.

---

## 4. User-Side Controls

### Enabling/disabling dynamic color

There is **no global system-level "disable dynamic color for all apps" toggle**. Users can:
- Change the source wallpaper/color in **Settings → Wallpaper & style** (applies a new palette, doesn't disable the feature).
- Select a preset color swatch instead of a wallpaper color — still uses the M3 tonal algorithm, just with a fixed seed.

**There is no per-app override for users** — they cannot tell an app "use static colors." Only the developer can opt the app out (by not calling the dynamic APIs).

### Themed icons — separate toggle

**Themed icons are a distinct, opt-in feature for the launcher**, toggled at **Settings → Wallpaper & style → Themed icons**. It is:
- Separate from dynamic color inside the app UI. Dynamic color affects your composables; themed icons affect your app's launcher icon.
- Requires you to ship a `<monochrome>` layer in your adaptive icon (API 33 declaration, but the layer is ignored on older launchers/OS).
- The toggle is off by default on many devices; turning it on tints the icon with the current palette's `system_accent1_200` (approximately).
- OEM launchers (e.g., Samsung One UI) may not support the themed icon toggle at all even on API 33+ devices.

### Implementation for themed icons (API 33, independent of dynamic color in your UI)

```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" /> <!-- API 33+ -->
</adaptive-icon>
```

---

## 5. Known Gotchas

### Low-contrast pairs on muted wallpapers

On desaturated wallpapers (grayscale photos, SPRITZ style), the secondary and tertiary roles can become nearly identical hues with low saturation. `secondaryContainer` vs. `tertiaryContainer` may be perceptually indistinguishable. If you visually distinguish UI sections by container color, test with a gray wallpaper.

### Error color blending with primary

When the user's wallpaper is warm-toned (orange, red, brown), the generated `error` color shifts toward the same hue family as `primary`. Red error indicators can look intentionally branded rather than alarming. Override `error`/`errorContainer` if your app needs a consistently distinct error state.

### SplashScreen API + DynamicColors ordering bug

When using the AndroidX `SplashScreen` API, calling `DynamicColors.applyToActivitiesIfAvailable(this)` in `Application.onCreate()` can fail to apply colors if the launcher theme inherits from `Theme.SplashScreen`. The root cause is theme overlay application order. [GitHub issue #2555](https://github.com/material-components/material-components-android/issues/2555) was closed but not fully resolved for all configurations. **Workaround**: Apply dynamic color in the activity's `onCreate()` before `installSplashScreen()`, or use `DynamicColors.applyToActivityIfAvailable(activity)` directly in the activity.

### Samsung One UI divergence

- Samsung's One UI 4.0+ exposes its own "Color Palette" feature that nominally maps to the same `system_accent*` palette slots.
- Starting with One UI 6 (shipped on Android 14 devices), multiple users and a GitHub bug report ([material-components #3924](https://github.com/material-components/material-components-android/issues/3924)) confirm that `system_accent*` colors are not being respected correctly by Material Components 1.11.0+ on Samsung hardware. Apps revert to a default blue palette regardless of wallpaper selection.
- Samsung does not fully expose the six Android 13 style variants in its settings UI.
- **Bottom line**: Do not assume dynamic color produces brand-coherent output on Samsung devices. If brand accuracy matters, apply a partial override (see Section 6) or fall back to a static scheme conditionally.

### Tablets and foldables

No officially documented behavior differences for dynamic color specifically. The palette generation process is identical. The main risk is that tablets use different wallpapers (often landscape crops) so the extracted seed color may differ from what users see on their phone. No documented issues specific to fold/unfold configuration changes interacting with color refreshes.

### `@color` resources cannot reference `?attr` attributes

If you use Views alongside Compose, note that `@color` XML resource files cannot dereference Material3 `?attr/colorPrimary` etc. at static resource inflation time (the Chrome team hit this). You must use `Context.getColor()` at runtime or use theme-resolved attributes in layouts/drawables.

---

## 6. Recommended Patterns

### Google's own guidance hierarchy

1. **Use dynamic color by default** if your app has no strong brand color requirements.
2. **Use partial overrides** to lock specific roles (e.g., primary) to brand values while letting the rest adapt.
3. **Provide a harmonized custom color** for semantic-but-non-standard colors (e.g., a green "success" indicator) using `HarmonizedColors`.
4. **Use a fully static scheme** if brand fidelity is non-negotiable and dynamic output cannot be validated.

### Canonical Compose dynamic color implementation with static fallback

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // make this a flag you can flip
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```

### Partial override: lock brand colors, keep dynamic surface/neutrals

```kotlin
val base = if (darkTheme) dynamicDarkColorScheme(context)
           else dynamicLightColorScheme(context)

val colorScheme = base.copy(
    primary            = BrandPrimary,
    onPrimary          = BrandOnPrimary,
    primaryContainer   = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    error              = FixedErrorRed,
    onError            = Color.White,
    errorContainer     = FixedErrorRedContainer,
    onErrorContainer   = FixedOnErrorRedContainer,
)
```

This is **not an officially documented API call** in Google's codelabs, but `ColorScheme.copy()` is a standard Kotlin data-class method and is the idiomatic Compose approach. The Chrome team's blog post describes an equivalent strategy.

### Harmonizing a custom "non-theme" color (e.g., success green)

For colors that live outside the 29-slot scheme (e.g., a custom attribute), use `MaterialColors.harmonizeWithPrimary()` from the Views-side Material library, or shift the hue toward the dynamic primary at runtime:

```kotlin
// Views-side (works in hybrid apps)
val harmonizedSuccess = MaterialColors.harmonizeWithPrimary(context, mySuccessColor)
```

Compose doesn't yet have a first-party harmonization API; you either call the Views-side utility or implement the HCT hue rotation yourself.

### Conditional opt-out for brand-critical screens

```kotlin
// Use dynamic color everywhere except the brand landing/splash screen
@Composable
fun BrandLandingScreen() {
    MaterialTheme(colorScheme = LightColorScheme) {  // force static for this subtree
        // ...
    }
}
```

`MaterialTheme` can be nested; inner wins. This lets you apply dynamic color at the root but override it for specific subtrees.

### When to strip dynamic color entirely

Google does not publish a bright-line rule, but the Now in Android case study and Chrome team blog imply:

- **Strip if**: Brand color is legally or contractually required to render faithfully (financial, healthcare branding, licensed team colors, etc.).
- **Strip if**: Your app's primary visual language is color-coded status (maps, data viz, medical UI) where arbitrary palette shifts break meaning.
- **Keep with partial override if**: You have a recognizable primary brand color but the rest of the UI can adapt.
- **Keep fully dynamic if**: The app is utility/productivity and personalization benefit outweighs brand precision.

---

## Sources

- [Material Design 3 in Compose — Android Developers](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Enable users to personalize their color experience (Views) — Android Developers](https://developer.android.com/develop/ui/views/theming/dynamic-colors)
- [Dynamic color — Android Open Source Project (AOSP)](https://source.android.com/docs/core/display/dynamic-color)
- [Color roles — Material Design 3](https://m3.material.io/styles/color/roles)
- [Color roles and tokens — Wear Android Developers](https://developer.android.com/design/ui/wear/guides/styles/color/roles-tokens) *(source of the official "3:1 minimum" claim)*
- [Adding dynamic color to your app — Google Codelabs](https://codelabs.developers.google.com/codelabs/apply-dynamic-color)
- [Implementing Dynamic Color: Lessons from the Chrome team — Android Developers Blog](https://android-developers.googleblog.com/2022/05/implementing-dynamic-color-lessons-from.html)
- [Now in Android: a Material 3 case study — Android Developers Medium](https://medium.com/androiddevelopers/now-in-android-a-material-3-case-study-21e44bdfd2bc)
- [Basic Color Harmonization in Android Views — Google Codelabs](https://codelabs.developers.google.com/harmonize-color-android-views)
- [DynamicColors API reference — Android Developers](https://developer.android.com/reference/com/google/android/material/color/DynamicColors)
- [HarmonizedColorAttributes API reference — Android Developers](https://developer.android.com/reference/com/google/android/material/color/HarmonizedColorAttributes)
- [Adaptive icons (themed icons) — Android Developers](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Android 13 Features: Themed icons — Android Developers](https://developer.android.com/about/versions/13/features)
- [material-components-android Color.md — GitHub](https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md)
- [SplashScreen + DynamicColors bug #2555 — GitHub](https://github.com/material-components/material-components-android/issues/2555)
- [One UI 6 dynamic color regression #3924 — GitHub](https://github.com/material-components/material-components-android/issues/3924)
