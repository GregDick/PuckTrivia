# AGP 9 Built-in Kotlin and kotlin-parcelize Compatibility

> **AGP Version**: 9.0.1 / 9.1.x  
> **Gradle Version**: 9.1.0  
> **KGP Bundled by AGP 9**: 2.2.10  
> **Last Updated**: 2026-06-10  
> **Official Documentation**:
> - [AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
> - [Migrate to Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
> - [Parcelable Implementation Generator](https://developer.android.com/kotlin/parcelize)
> - [ViewModel SavedState](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)

---

## Overview

AGP 9.0 introduces **built-in Kotlin support enabled by default**. AGP now carries a runtime
dependency on the Kotlin Gradle Plugin (KGP) at version 2.2.10. This eliminates the need to
apply `org.jetbrains.kotlin.android`, but it also changes how every other KGP-family compiler
plugin (including `kotlin-parcelize`) must be declared.

---

## Question 1 — Does AGP 9 built-in Kotlin officially support kotlin-parcelize?

### Short answer

Not as a DSL flag or first-class built-in feature. `kotlin-parcelize` remains a separate Kotlin
compiler plugin that must still be applied explicitly. There is **no `android {}` DSL flag, no
`android.builtInParcelize`, no `gradle.properties` switch**, and no `kotlin { compilerOptions }`
argument that activates it.

### The root cause of both failure modes

AGP 9 adds KGP to the buildscript classpath as an **unversioned** runtime dependency. Because
Gradle's plugin resolution stores the KGP JAR without a version marker, any sub-plugin that ships
inside the same Kotlin distribution (including `kotlin-parcelize`) is also on the classpath without
a resolvable version.

**Failure mode A — explicit version in plugins block:**

```kotlin
// app/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21"  // ← ERROR
}
```

Gradle refuses: *"The request for this plugin could not be satisfied because the plugin is already
on the classpath with an unknown version, so compatibility cannot be checked."*

Reason: the plugin JAR is already present (loaded by AGP's KGP dependency), but has no version
recorded; Gradle cannot confirm compatibility with the requested version and throws.

**Failure mode B — id only, no root-level declaration, no version:**

```kotlin
// app/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.plugin.parcelize")  // no version — build proceeds
}
```

The build appears to succeed. However, Gradle's `plugins {}` block with no version resolves only
from the **Gradle Plugin Portal** or from a classpath entry that has a version. Because the
classpath copy carries no version, Gradle falls back to the portal — and may silently skip the
plugin if it cannot resolve a matching artifact from there, or applies it but the compiler plugin
hook does not fire because the artifact loaded is not registered against the active Kotlin
compilation session.

Result: `@Parcelize` is an unresolved reference; no `writeToParcel`/`CREATOR` is generated.

### What the official migration guide says about compiler plugins

The [migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin) explicitly
covers only `kotlin-kapt` (replaced by `com.android.legacy-kapt` or KSP) and the removal of
`org.jetbrains.kotlin.android`. There is **no documented mechanism for applying
`kotlin-parcelize` under built-in Kotlin**. Issue
[#389977429](https://issuetracker.google.com/issues/389977429) on the Google Issue Tracker is
titled "Add test coverage for kotlin-parcelize plugin with AGP's Built-in Kotlin" — its existence
implies that at the time of writing (June 2026) the pairing is **not yet validated with
comprehensive test coverage**, and the issue is still open.

---

## Question 2 — Known issue? Recommended workaround?

The closest public acknowledgment is issue tracker entry
[#389977429](https://issuetracker.google.com/issues/389977429) (sign-in required to view full
details). No official workaround using built-in Kotlin + parcelize has been published in the AGP
9.0.1 or 9.1.x release notes.

### Workaround that works today (confirmed by community usage at AGP 9.2.1)

Declare an **explicit version of KGP in the root `buildscript` dependencies block**. This gives
Gradle a versioned classpath entry, which allows the `plugins {}` block in the module — with no
version — to match that entry, and the parcelize compiler plugin activates correctly.

#### Step 1 — `gradle/libs.versions.toml`

Add (or confirm) the parcelize plugin alias referencing the same `kotlin` version entry you
already have:

```toml
[versions]
kotlin = "2.0.21"   # already present in this project

[plugins]
# existing entries...
kotlin-compose   = { id = "org.jetbrains.kotlin.plugin.compose",    version.ref = "kotlin" }
# ADD:
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize",  version.ref = "kotlin" }
```

#### Step 2 — Root `build.gradle.kts`

Add a `buildscript` block **above** the `plugins {}` block that pins KGP to the same version.
This gives the classpath an explicit version string, which resolves the "unknown version" problem:

```kotlin
// build.gradle.kts  (root)
buildscript {
    dependencies {
        // Pin KGP explicitly so parcelize (and any other KGP compiler plugin) can be
        // resolved by id-only declarations in submodule plugins {} blocks.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.parcelize)     apply false   // ← ADD
    alias(libs.plugins.hilt.android)         apply false
    alias(libs.plugins.ksp)                  apply false
}
```

#### Step 3 — `app/build.gradle.kts`

Apply via the catalog alias (no version inline):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)   // ← replaces id("...") bare string
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}
```

Remove the bare `id("org.jetbrains.kotlin.plugin.parcelize")` that is currently on line 6 of
`app/build.gradle.kts`.

> **Why the `buildscript` block is the key:** Gradle's `plugins {}` block can use an unversioned
> alias only when the plugin is already on the buildscript classpath *with a version*. The
> `buildscript { dependencies { classpath(...) } }` call registers the JAR with a version string,
> enabling the `plugins {}` resolution to succeed. This is the same mechanism documented in the
> AGP 9.0 release notes for overriding the bundled KGP to a newer version.

#### Optional: verify the runtime dependency

The `kotlin-parcelize` Gradle plugin arranges the compiler plugin JAR automatically; a separate
`implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:…")` dependency is *not* required
and should not be added manually.

---

## Question 3 — Is switching back to `org.jetbrains.kotlin.android` (KGP) still supported in AGP 9?

### Supported — but deprecated

You can opt the **entire project** back to classic KGP by setting in `gradle.properties`:

```properties
android.builtInKotlin=false
android.newDsl=false
```

With both flags set:
- `org.jetbrains.kotlin.android` can be applied again without a "duplicate kotlin extension" error.
- `org.jetbrains.kotlin.plugin.parcelize` works exactly as it did under AGP 8.x.
- `android { kotlinOptions { } }` syntax is available again.

**Critical caveat:** Both flags are **removed in AGP 10.0** (expected late 2026). This path is a
time-limited escape hatch, not a forward-compatible solution.

### What migration back would entail for this project

The project currently has no `org.jetbrains.kotlin.android` applied; it was already removed as
part of the original AGP 9 migration. Re-adding it requires:

1. Add `android.builtInKotlin=false` and `android.newDsl=false` to `gradle.properties`.
2. Re-add the KGP plugin entry to `libs.versions.toml`:
   ```toml
   kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
   ```
3. Re-add `alias(libs.plugins.kotlin.android) apply false` to the root `plugins {}`.
4. Add `alias(libs.plugins.kotlin.android)` to `app/build.gradle.kts`.
5. Add `alias(libs.plugins.kotlin.parcelize) apply false` to the root block.
6. Add `alias(libs.plugins.kotlin.parcelize)` to the app module.

This restores the pre-AGP-9 setup completely, but must be revisited before upgrading to AGP 10.

> **Recommendation:** Do not use this path for new work. The buildscript-classpath workaround
> in Question 2 keeps built-in Kotlin intact and is forward-compatible.

---

## Question 4 — Storing custom objects in SavedStateHandle without Parcelable

### What SavedStateHandle natively supports

`SavedStateHandle` accepts the same types as `android.os.Bundle`: all primitives and their arrays,
`String`, `CharSequence`, `Parcelable`, `Serializable`, `Bundle`, `ArrayList`, `SparseArray`,
`Binder`, `Size`, and `SizeF`.

### java.io.Serializable — viability and caveats

`Serializable` is supported natively — `savedStateHandle.set("key", mySerializableObject)` will
compile and survive process death without any additional plugin. However:

- **Performance**: Serializable uses Java reflection and is significantly slower than Parcelable for
  objects that are serialized/deserialized frequently (e.g., on every navigation back-stack
  restoration).
- **Fragility**: Any change to field names, types, or class name breaks deserialization of saved
  state unless `serialVersionUID` is managed carefully.
- **No code generation**: Boilerplate-free only for trivial objects; complex object graphs require
  careful `@Transient` annotations and custom `readObject`/`writeObject` if needed.

For simple, stable data classes used purely as UI state, `Serializable` is a reasonable short-term
workaround.

### Preferred modern alternative — `@Serializable` + `savedstate-ktx`

Introduced in `androidx.savedstate:savedstate-ktx:1.3.0` (May 2025), the `saved {}` property
delegate integrates KotlinX Serialization directly with `SavedStateHandle` and
`SavedStateRegistryOwner`:

```kotlin
// gradle/libs.versions.toml
[versions]
savedstate = "1.5.0"    # latest as of June 2026

[libraries]
androidx-savedstate-ktx = { group = "androidx.savedstate", name = "savedstate-ktx", version.ref = "savedstate" }
kotlinx-serialization-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-core", version = "1.7.3" }
```

```kotlin
// app/build.gradle.kts — add to plugins block
id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
// ...and to dependencies:
implementation(libs.androidx.savedstate.ktx)
implementation(libs.kotlinx.serialization.core)
```

```kotlin
// Usage in ViewModel
@Serializable
data class FilterState(val query: String = "", val minRating: Int = 0)

class MyViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    var filter by savedStateHandle.saved { FilterState() }
        private set
}
```

This approach:
- Requires zero Parcelable or Serializable boilerplate.
- Is fully supported with AGP 9 built-in Kotlin (no parcelize compiler plugin needed).
- Survives process death automatically.
- Is the direction the official SavedState documentation points for complex custom types.

> **Note**: `kotlin.plugin.serialization` has the same "unknown version" classpath problem as
> parcelize when used with AGP 9 built-in Kotlin. Apply the same buildscript-classpath fix from
> Question 2: add `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")` in the root
> `buildscript` block, then declare the serialization plugin alias in the root plugins block with
> `apply false`, and apply it in the module.

---

## Decision Summary

| Approach | Forward-compatible | Effort | Notes |
|---|---|---|---|
| **buildscript classpath pin + parcelize alias** (Q2 workaround) | Yes (until AGP 10 rethinks it) | Low — 3 file edits | Best short-term path to keep @Parcelize |
| **`@Serializable` + `savedstate-ktx`** | Yes | Medium — new dependency, refactor data classes | Recommended long-term; avoids parcelize entirely |
| **`java.io.Serializable`** | Yes | Minimal | Acceptable for simple stable DTOs; no codegen |
| **Opt out: `builtInKotlin=false`** | No — removed in AGP 10 | Low | Only for emergency unblocking |

---

## Sources

- [AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Migrate to Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Parcelable Implementation Generator](https://developer.android.com/kotlin/parcelize)
- [ViewModel Saved State](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)
- [SavedState Jetpack Releases](https://developer.android.com/jetpack/androidx/releases/savedstate)
- [Kotlin Parcelize Plugin — Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.plugin.parcelize)
- [Issue #389977429: Add test coverage for kotlin-parcelize with AGP built-in Kotlin](https://issuetracker.google.com/issues/389977429)
- [Gradle Issue #20084: Plugin already on classpath with unknown version](https://github.com/gradle/gradle/issues/20084)
- [JetBrains Blog: Update your Kotlin projects for AGP 9.0](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- [libs.versions.toml example at AGP 9.2.1 (fragmject)](https://raw.githubusercontent.com/miaowmiaow/fragmject/master/gradle/libs.versions.toml)
