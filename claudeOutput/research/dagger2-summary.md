# Dagger 2 Documentation Summary

> **Version Documented**: 2.59.2 (latest as of March 2026)
> **Last Updated**: 2026-03-26
> **Official Documentation**: [dagger.dev](https://dagger.dev/)
> **GitHub**: [google/dagger](https://github.com/google/dagger)

---

## Overview

Dagger 2 is a fully static, compile-time dependency injection (DI) framework for Java, Kotlin, and Android. It is maintained by Google. All dependency graph wiring is validated and generated at compile time — there is no reflection at runtime, which makes it both fast and safe.

**Hilt** is the recommended way to use Dagger on Android. It is a first-party library built on top of Dagger that eliminates most of the boilerplate by auto-generating the standard Android component hierarchy. For new Android projects, **use Hilt, not raw Dagger 2**.

> **Note**: `dagger.android` (the older Android integration library) is in maintenance mode and no longer under active development. Use Hilt instead.

---

## Latest Version

| Artifact | Version | Released |
|---|---|---|
| `com.google.dagger:hilt-android` | **2.59.2** | February 20, 2025 |
| `com.google.dagger:dagger` | **2.59.2** | February 20, 2025 |
| `com.google.dagger:hilt-compiler` | **2.59.2** | February 20, 2025 |

---

## KSP vs kapt

KSP (Kotlin Symbol Processing) is the **recommended** annotation processor for Dagger/Hilt with Kotlin. It is significantly faster than kapt (Kotlin Annotation Processing Tool). Dagger supports KSP as of version `2.48`.

> **Important**: Kotlin 2.0 does not enable kapt by default. KSP is the forward-looking choice.

**KSP plugin version for Kotlin 2.0.21** (this project's Kotlin version):

```
com.google.devtools.ksp version: 2.0.21-1.0.26
```

The KSP version format (for older versioning scheme) is `<KotlinVersion>-<KspPatchVersion>`. The latest patch release for Kotlin 2.0.21 is `2.0.21-1.0.26`.

---

## Installation — Gradle Kotlin DSL (build.gradle.kts)

This project uses **Gradle Kotlin DSL**, **Kotlin 2.0.21**, **AGP 9.0.1**, and **Gradle 9.1.0**.

### Root-level `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.26" apply false
}
```

### App-level `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    // ... existing config
}

dependencies {
    // Hilt runtime
    implementation("com.google.dagger:hilt-android:2.59.2")

    // Hilt compiler (KSP — replaces kapt)
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    // Hilt for instrumentation tests
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.59.2")

    // Hilt for local unit tests
    testImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspTest("com.google.dagger:hilt-compiler:2.59.2")
}
```

> **Note**: If you are using plain Dagger 2 without Hilt (not recommended for Android), replace the Hilt dependencies with:
> ```kotlin
> implementation("com.google.dagger:dagger:2.59.2")
> ksp("com.google.dagger:dagger-compiler:2.59.2")
> ```

---

## Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
hilt = "2.59.2"
ksp = "2.0.21-1.0.26"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }

[plugins]
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

With the version catalog, `build.gradle.kts` plugin blocks use aliases:

```kotlin
// Root build.gradle.kts
plugins {
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

// app/build.gradle.kts
plugins {
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
```

---

## Core Concepts

### `@Inject`

Marks a constructor, field, or method that Dagger should use to satisfy or fulfill a dependency.

```kotlin
class UserRepository @Inject constructor(
    private val apiService: ApiService
)
```

### `@Module` and `@Provides`

Modules tell Dagger how to create instances of types it cannot construct directly (interfaces, third-party classes, objects requiring configuration).

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
```

### `@Binds`

A more efficient alternative to `@Provides` for binding an interface to its implementation. Preferred over `@Provides` when no construction logic is needed.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository
}
```

### `@Component` (raw Dagger 2 — not needed with Hilt)

The root interface that generates the dependency graph. Dagger generates an implementation class prefixed with `Dagger`.

```kotlin
@Component(modules = [AppModule::class])
interface AppComponent {
    fun inject(activity: MainActivity)
}

// Usage
val component = DaggerAppComponent.create()
component.inject(this)
```

---

## Hilt Quick Start (Recommended for Android)

Hilt auto-generates the standard Android component hierarchy, so you do not need to write `@Component` interfaces manually.

### 1. Annotate your `Application` class

```kotlin
@HiltAndroidApp
class MyApplication : Application()
```

Register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
```

### 2. Inject into Android framework classes

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // userRepository is ready to use here
    }
}
```

`@AndroidEntryPoint` supports: `Activity`, `Fragment`, `View`, `Service`, `BroadcastReceiver`.

### 3. Inject into a ViewModel

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel()
```

Consume in the Activity/Fragment:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
}
```

---

## Hilt Standard Components and Scopes

Hilt provides a predefined component hierarchy. Each component has a corresponding scope annotation that makes a binding a singleton within that component's lifetime.

| Component | Created in | Scope annotation | Destroyed in |
|---|---|---|---|
| `SingletonComponent` | `Application#onCreate()` | `@Singleton` | `Application` destroyed |
| `ActivityRetainedComponent` | `Activity#onCreate()` | `@ActivityRetainedScoped` | `Activity#onDestroy()` |
| `ViewModelComponent` | `ViewModel` created | `@ViewModelScoped` | `ViewModel` destroyed |
| `ActivityComponent` | `Activity#onCreate()` | `@ActivityScoped` | `Activity#onDestroy()` |
| `FragmentComponent` | `Fragment#onAttach()` | `@FragmentScoped` | `Fragment#onDestroy()` |
| `ServiceComponent` | `Service#onCreate()` | `@ServiceScoped` | `Service#onDestroy()` |

### `@InstallIn`

Every `@Module` must declare which component it is installed in:

```kotlin
@Module
@InstallIn(SingletonComponent::class)  // available app-wide
object AppModule { ... }

@Module
@InstallIn(ActivityComponent::class)  // available within an Activity's lifetime
object ActivityModule { ... }
```

---

## Scoping

Without a scope annotation, Dagger creates a new instance of a binding every time it is requested.

```kotlin
// New instance on each injection point
class AnalyticsService @Inject constructor()

// One instance per application lifetime (Hilt)
@Singleton
class UserCache @Inject constructor()

// One instance per ViewModel lifetime
@ViewModelScoped
class FormStateHolder @Inject constructor()
```

---

## Testing with Hilt

### Instrumented tests (`androidTest`)

```kotlin
@HiltAndroidTest
class MainActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var userRepository: UserRepository

    @Before
    fun setup() {
        hiltRule.inject()
    }
}
```

### Replace bindings in tests

```kotlin
@UninstallModules(NetworkModule::class)
@HiltAndroidTest
class MainActivityTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeNetworkModule {
        @Provides
        fun provideApiService(): ApiService = FakeApiService()
    }
}
```

---

## Build Performance Tip

The Hilt Gradle plugin provides an aggregating task that can improve incremental build times. Add this to your `app/build.gradle.kts`:

```kotlin
hilt {
    enableAggregatingTask = true
}
```

---

## Best Practices

- **Use Hilt for Android projects** — do not use raw Dagger 2 or `dagger.android` for new code.
- **Use KSP over kapt** — KSP is faster and is the default going forward with Kotlin 2.0+.
- **Prefer `@Binds` over `@Provides`** for interface-to-implementation bindings; it generates less code.
- **Use `@Singleton` sparingly** — scope only objects that are expensive to create or that hold shared state.
- **Use `@ViewModelScoped`** for dependencies that should live and die with a ViewModel.
- **Do not inject into Compose functions directly** — inject into ViewModels and pass state down via Compose parameters or state holders.
- When combining Hilt with other annotation processors, use `+=` when adding annotation processor arguments to avoid overwriting Hilt's internal settings:
  ```kotlin
  javaCompileOptions {
      annotationProcessorOptions {
          arguments += mapOf("foo" to "bar")
      }
  }
  ```

---

## Sources

- [Dagger official site — dagger.dev](https://dagger.dev/)
- [Dagger KSP setup — dagger.dev/dev-guide/ksp.html](https://dagger.dev/dev-guide/ksp.html)
- [Hilt overview — dagger.dev/hilt/](https://dagger.dev/hilt/)
- [Hilt Gradle setup — dagger.dev/hilt/gradle-setup.html](https://dagger.dev/hilt/gradle-setup.html)
- [Dagger Android guide — dagger.dev/dev-guide/android.html](https://dagger.dev/dev-guide/android.html)
- [Dagger releases — github.com/google/dagger/releases](https://github.com/google/dagger/releases)
- [KSP releases — github.com/google/ksp/releases](https://github.com/google/ksp/releases)
- [KSP Maven Central (2.0.21-1.0.26) — central.sonatype.com](https://central.sonatype.com/artifact/com.google.devtools.ksp/symbol-processing-gradle-plugin/2.0.21-1.0.25)
- [KSP quickstart — kotlinlang.org/docs/ksp-quickstart.html](https://kotlinlang.org/docs/ksp-quickstart.html)
