# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Puck Trivia is an Android app built with Kotlin and Jetpack Compose. It is currently at the initial template stage (single-module Gradle project).

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew test --tests "com.example.pucktrivia.ExampleUnitTest"  # Run a single test class
```

## Architecture

- **Single module**: `:app` — all source code lives under `app/src/main/java/com/example/pucktrivia/`
- **UI framework**: Jetpack Compose with Material 3
- **Theme**: Defined in `app/src/main/java/com/example/pucktrivia/ui/theme/` (Color.kt, Theme.kt, Type.kt)
- **Entry point**: `MainActivity` — single activity using `setContent` with Compose
- **Build config**: Gradle Kotlin DSL with a version catalog at `gradle/libs.versions.toml`
- **Min SDK**: 30, **Target SDK**: 36, **Gradle**: 9.1.0, **AGP**: 9.0.1, **Kotlin**: 2.0.21
