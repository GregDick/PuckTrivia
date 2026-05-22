# Journey Test Guide

Gemini-driven UI journey tests verify app flows end-to-end against a real build. Journeys
live under `app/src/journeysTest/journeys/` as `.journey.xml` files.

## Prerequisites

- **GCP auth** — run `gcloud auth application-default login` once before running journeys.
  The engine makes Gemini API calls; without Application Default Credentials it fails with
  a cryptic gRPC auth error.
- **JDK 21** — the journey tasks run on a JDK 21 launcher (scoped via toolchain in
  `app/build.gradle.kts`; the rest of the build is unaffected). On a clean CI runner without
  JDK 21, add the [foojay toolchain resolver](https://github.com/gradle/foojay-toolchains)
  so Gradle can auto-provision it.

## Running journeys

```bash
# Run all journeys
./gradlew :app:testJourneysTestDefaultDebugTestSuite

# Run a single journey — JOURNEYS_FILTER is a path RELATIVE TO THE SOURCE ROOT,
# matched as a prefix. It must include the journeys/ directory or it matches nothing
# (and reports a misleading "0 tests" with no error).
JOURNEYS_FILTER=journeys/regular_season_quiz.journey.xml ./gradlew :app:testJourneysTestDefaultDebugTestSuite
```
