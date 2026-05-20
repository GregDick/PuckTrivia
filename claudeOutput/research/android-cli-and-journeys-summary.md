# Android CLI and Journey Tests Documentation Summary

> **Version Documented**: Android CLI 0.7 (preview, April 2026); Journey tests preview in Android Studio Otter 3 Feature Drop (2025.2.3)
> **Last Updated**: 2026-05-15
> **Official Documentation**:
> - [Android CLI Overview](https://developer.android.com/tools/agents/android-cli)
> - [Journeys for Android Studio](https://developer.android.com/studio/gemini/journeys)

## Overview

This document combines two complementary Google tools for agent-driven Android development:

- **Android CLI** — an agent-first command-line tool that wraps the Android SDK (emulator management, build/deploy, UI inspection, knowledge base) with output formats optimized for LLM consumption. Google reports a 70% reduction in token usage and 3x faster task completion versus agents using raw SDK tooling.
- **Journey tests** — AI-powered functional UI tests written in natural-language XML. Gemini interprets each step at runtime against a live screenshot, performing taps/typing/swiping and verifying outcomes without selector-based code.

Together they enable a workflow where an agent observes the running app via Android CLI to inform journey authoring, then executes journeys via Gradle.

---

## Part 1: Android CLI

### Status

Preview. v0.7 was the first public release (April 16, 2026). Not yet GA.

### Installation

No prerequisites beyond an Android SDK. Android Studio is **not** required.

**macOS (Apple Silicon):**
```bash
curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
```

**macOS (Intel):**
```bash
curl -fsSL https://dl.google.com/android/cli/latest/darwin_x86_64/install.sh | bash
```

**Linux (x86_64):**
```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
```

**Windows (x86_64):**
```
curl.exe -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "%TEMP%\i.cmd" && "%TEMP%\i.cmd"
```

Global (all-users, requires sudo) variants are also available at `install_root.sh` / `install_admin.cmd`.

**Post-install:**
```bash
android update        # Pull latest version
android init          # Installs the android-cli skill (required for agent integration)
```

**Configuration** — `~/.androidrc` (macOS/Linux) or `%USERPROFILE%\.androidrc` (Windows):
```
--sdk=/path/to/android/sdk
```

**Authentication**: No API keys or Google sign-in required for the CLI itself. Auth is only needed for journey execution (see Part 2).

**OS limitations**:
- `android emulator` subcommands disabled on Windows.
- Windows PowerShell downloads unsupported (use `cmd.exe`).

### Command Reference

**Global flags**

| Flag | Purpose |
|------|---------|
| `--sdk=<path>` | Override default Android SDK location |
| `--version` / `-V` | Show CLI version |
| `-h` / `--help` | Help for any command or subcommand |

**Project management**

| Command | Purpose |
|---------|---------|
| `android create [--name=X] [--output=PATH] [template]` | Scaffold a new project |
| `android create list` | List available templates |
| `android create --dry-run --verbose empty-activity-agp-9` | Preview scaffold without writing files |
| `android describe [--project_dir=PATH]` | Analyze project; outputs JSON with APK paths |
| `android info` | Show default Android SDK path |

`describe` is the agent's primary way to discover where build outputs live.

**Emulator management**

| Command | Purpose |
|---------|---------|
| `android emulator create [--profile=medium_phone]` | Create an AVD |
| `android emulator create --list-profiles` | List device profiles |
| `android emulator list` | List existing AVDs |
| `android emulator start <device-name>` | Boot an AVD |
| `android emulator stop <device-serial>` | Stop a running emulator |

**App deployment**

```bash
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android run --apks=app-debug.apk --device=emulator-5554 --activity=.MainActivity
android run --apks=app-debug.apk --debug   # Attach debugger
```

`android run` does **not** build — it installs and launches a pre-built APK.

**SDK management**

```bash
android sdk install platforms/android-35 build-tools/35.0.0
android sdk install --canary system-images/android-35/google_apis/x86_64
android sdk list --all
android sdk update
android sdk remove build-tools/36.1.0
```

**UI inspection (the agent's perception layer)**

| Command | Output | Notes |
|---------|--------|-------|
| `android layout [--pretty] [--output=FILE] [--diff]` | JSON | Full UI hierarchy; `--diff` returns only changes since last call |
| `android screen capture [--output=FILE] [--annotate]` | PNG | `--annotate` draws numbered bounding boxes around UI elements |
| `android screen resolve --screenshot=ui.png --string="input tap #5"` | Plaintext | Translates label `#5` → `input tap 500 1000` |

**Agent UI loop:**
1. `android screen capture --output=ui.png --annotate` → labeled screenshot
2. Agent picks element to tap by label number
3. `android screen resolve --screenshot=ui.png --string="input tap #3"` → ADB tap command
4. Agent executes the ADB command or calls `layout` to read updated state

**Knowledge base and skills**

```bash
android docs search 'How do I set up navigation in Compose?'
android docs fetch kb://android/topic/navigation/overview

android skills add --all
android skills add --agent='claude' edge-to-edge
android skills list --long
android skills find 'performance'
```

Skills are markdown `SKILL.md` files installed into agent-specific directories. `android init` installs the foundational `android-cli` skill.

### I/O Behavior for Automation

- **stdout**: `layout` emits JSON, `screen capture` emits raw PNG bytes, `screen resolve` emits plaintext, `describe` emits JSON.
- **stderr**: Error messages and stack traces (anonymized traces are telemetered to Google).
- **Exit codes**: Not explicitly documented; assume POSIX convention.
- **File output**: Any command accepting `--output` saves to that path instead of stdout — safer for binary payloads in pipelines.
- **No stdin interaction**: All commands are flag-driven.

### Telemetry

Google collects: command names, flag names (not values), predefined option values (profile names, agent names), anonymized stack traces. Not collected: command output, file paths, project names, package identifiers, Maven coordinates. No documented opt-out.

---

## Part 2: Journey Tests

### What a Journey Is

An AI-powered functional test expressed in natural-language XML. At runtime Gemini receives a screenshot, reads the step description, decides the action (tap/type/swipe), executes it, and moves on.

| Dimension | Journey | Espresso / Compose UI Test | Macrobenchmark |
|-----------|---------|----------------------------|----------------|
| Language | Natural language (XML) | Kotlin/Java code | Kotlin code |
| Selector mechanism | Gemini vision + reasoning | Semantic tree / resource IDs | N/A |
| Brittleness | Low — tolerates layout changes | High — breaks on ID/structure changes | N/A |
| Assertion style | Vision-based | Code assertions on composable state | Timing/frame metrics |
| Requires Gemini/AI | Yes | No | No |
| Primary purpose | Functional regression via AI | Functional regression via code | Performance measurement |

Journeys do not replace unit tests or Macrobenchmark — they cover end-to-end visual correctness without selector fragility.

### Status

Studio Labs experimental feature in **Android Studio Otter 3 Feature Drop (2025.2.3)**. Enabled via Studio Labs settings. Requires a signed-in Google developer account with Gemini in Android Studio active.

### File Format

XML, extension `.journey.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<journey name="LoginJourneyTest">
    <description>Tests the login screen's initial state, input validation, and login button behavior.</description>
    <actions>
        <action>Verify that the email text input field is empty.</action>
        <action>Type 'user@example.com' into the email text input field.</action>
        <action>Type 'password123' into the password field.</action>
        <action>Tap the 'Log In' button. Verify the home screen appears.</action>
    </actions>
</journey>
```

Root: `<journey name="...">`. Children: `<description>` (optional) and `<actions>` containing one or more `<action>` elements.

### Directory Location

```
app/src/journeysTest/
    journeys/
        login_journey_test.journey.xml
        home/
            home_navigation.journey.xml
```

When you create your first journey via **New > Journey Test** in Android Studio, the IDE adds AGP Test Suites support and creates the source set automatically. `JOURNEYS_FILTER` uses subdirectory names as prefix filters.

### Authoring

Journeys are written manually as XML — no recorder, no auto-generation. Android Studio offers two editing modes:
- **Design view**: One text field per step.
- **Code view**: Direct XML editing with IDE gutter run buttons.

**Step-writing guidelines:**

| Avoid | Prefer |
|-------|--------|
| "Select the dismiss button" | "Tap 'Dismiss'" |
| "Type 'celery'" | "Type 'celery' in the search bar at the top of the home screen" |
| "Select the send button" | "Send the email by tapping the submit button. This should close the email and return you to the inbox." |
| "Go to the shopping cart" | "Tap on the shopping cart icon which will take you to the shopping cart page. Verify it contains zero items" |

Key rules:
- **Do not include "launch the app"** — the app is already foregrounded.
- **Include the expected outcome** in each step rather than separating action and assertion.
- **One discrete action per step.**
- Refine based on the "Action Taken" and "Reasoning" fields in the IDE results panel.

**Supported actions:** tap, type, swipe/scroll.
**Unsupported / unreliable:** pinch/zoom, multi-finger gestures, long press, double tap, rotation/folding, cross-step memory, counting items, conditional logic.

### Gradle Configuration

Requires **AGP 9.0.0+**. Add to `app/build.gradle.kts`:

```kotlin
android {
    testSuites {
        create("journeysTest") {
            targetVariants += listOf("debug")   // or "demoDebug", etc.
        }
    }
}
```

The IDE adds this automatically when you create your first journey. `targetVariants` must align with the variant you intend to test.

### Execution

**One-time auth setup** — journeys call Gemini APIs, which need Google Cloud credentials:

```bash
# Install gcloud CLI first: https://cloud.google.com/sdk/docs/install

# Developer use:
gcloud auth application-default login

# CI / service account:
gcloud auth application-default login --impersonate-service-account SA_EMAIL
# SA and its admin need the 'Service Account Token Creator' IAM role.
# Requires IAM Service Account Credentials API enabled on the GCP project.
```

**Run commands:**

```bash
# All journeys
./gradlew :app:testJourneysTestDefaultDebugTestSuite

# Single journey
JOURNEYS_FILTER=login_journey_test.journey.xml ./gradlew :app:testJourneysTestDefaultDebugTestSuite

# Subdirectory
JOURNEYS_FILTER=home ./gradlew :app:testJourneysTestDefaultDebugTestSuite

# Pre-installed app (not built from this project)
JOURNEYS_CUSTOM_APP_ID=com.example.pucktrivia ./gradlew :app:testJourneysTestDefaultDebugTestSuite
```

Gradle task pattern: `:app:testJourneysTest{SuiteName}{Variant}TestSuite`. Default suite name is `Default`.

### Results

**In Android Studio**: A "Journeys Test" panel shows each step's screenshot, chosen action, and Gemini's reasoning text.

**From CLI**:
- Terminal logs with pass/fail
- HTML report in `app/build/reports/`
- JUnit-compatible XML in `app/build/test-results/`
- Screenshots captured at each step

JUnit XML is compatible with GitHub Actions, Jenkins, etc.

**Failure mode**: After max attempts, a step fails with `"Could not successfully complete the action in max allowed attempt"`. Fix by splitting into smaller, more specific steps.

**Known limitations:**
- All app permissions granted automatically (can't test permission-denial flows).
- On Android 15 (API 35), an "Unsafe App Blocked" warning for "AndroidX Crawler" requires manual dismissal or disabling **Verify apps over USB** — an automation blocker for fully headless CI.
- No machine-readable assertion detail beyond the failure message string.
- Sequential execution only; no branching or looping.

---

## Part 3: Building a Journey-Test Agent

### How the Pieces Combine

- **Android CLI** handles infrastructure: emulator, APK deployment, live UI observation.
- **Journeys** handle test specification and execution.

There is **no direct bridge** — Android CLI has no `android journeys run` subcommand. The connection is through the standard Gradle task.

### Minimal Workflow: Create → Run → Parse

**Phase 1: Setup (one-time)**
```bash
curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
android init
gcloud auth application-default login
android emulator create --profile=medium_phone
android emulator start medium_phone
# Note serial from: adb devices
```

**Phase 2: Build and deploy**
```bash
./gradlew :app:assembleDebug
android run --apks=app/build/outputs/apk/debug/app-debug.apk --device=emulator-5554
```

**Phase 3: Observe app UI**
```bash
android screen capture --output=screen.png --annotate
android layout --pretty --output=hierarchy.json
# Agent reads both to understand available elements
```

**Phase 4: Author the journey**

Write `app/src/journeysTest/journeys/quiz_flow.journey.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<journey name="PuckTriviaQuizFlow">
    <description>Verifies a user can start a game and answer a question.</description>
    <actions>
        <action>Tap the 'Start Game' button.</action>
        <action>Verify a question is displayed on the screen.</action>
        <action>Tap the first answer option. Verify feedback is shown.</action>
    </actions>
</journey>
```

Ensure `app/build.gradle.kts` contains the `testSuites` block.

**Phase 5: Execute**
```bash
JOURNEYS_FILTER=quiz_flow.journey.xml \
  ./gradlew :app:testJourneysTestDefaultDebugTestSuite
```

**Phase 6: Parse results**
```bash
echo "Exit code: $?"   # 0 = pass, non-zero = fail/build error
cat app/build/test-results/testJourneysTestDefaultDebugTestSuite/TEST-*.xml
open app/build/reports/tests/testJourneysTestDefaultDebugTestSuite/index.html
```

### Gaps and Blockers

| Gap | Description | Workaround |
|-----|-------------|------------|
| **Emulator requirement** | No headless/containerized execution. | Use `android emulator create` + `start` before Gradle. |
| **GCP auth required** | `gcloud auth application-default login` must run first. | CI: provision service account with Token Creator role; impersonate via flag. |
| **No `android journeys` subcommand** | Execution goes through Gradle. | Call Gradle task directly. |
| **AGP 9.0.0+ required** | Project must declare `testSuites` block. | Puck Trivia is on AGP 9.0.1 — not a blocker. |
| **Studio-driven first-time setup** | IDE adds `testSuites` block and source set automatically. | Hand-author the block and directory. |
| **API 35 permission dialog** | "Unsafe App Blocked" requires dismissal. | Use API 34 emulators for headless CI, or disable Verify apps over USB. |
| **No structured assertion output** | Only pass/fail + failure message string in JUnit XML. | Parse `<failure message="...">`; design steps with clear outcomes. |
| **Non-determinism** | Same journey can take different paths. | Describe outcomes, not paths. Use `android layout --diff` between steps to verify state programmatically. |
| **No counting/branching/memory** | Steps are independent and linear. | Keep journeys narrow; split complex flows into multiple journeys. |
| **No `android` UI automation primitives** | `screen resolve` produces ADB command strings, not actions. | Chain into `adb shell input tap X Y`. |
| **No Windows emulator support** | `android emulator` disabled on Windows. | Use macOS or Linux. |

### Puck Trivia Specifics

Already satisfied:
- AGP 9.0.1 (above 9.0.0 minimum).
- `applicationId = com.example.pucktrivia` — pass to `JOURNEYS_CUSTOM_APP_ID` if testing a pre-installed build.
- minSdk 30, targetSdk 36 — use an API 34 emulator image to avoid the API 35 "Unsafe App Blocked" dialog in CI.

Still needed:
- `testSuites { create("journeysTest") { targetVariants += listOf("debug") } }` in `app/build.gradle.kts`.
- `app/src/journeysTest/journeys/` directory.
- Studio Labs feature enabled (IDE-only setting).

---

## Sources

- [Android CLI Overview](https://developer.android.com/tools/agents/android-cli)
- [Android CLI Download / Archive](https://developer.android.com/tools/agents/android-cli/archive)
- [Android CLI Release Notes](https://developer.android.com/tools/agents/android-cli/release-notes)
- [Android Agent Tools Overview](https://developer.android.com/tools/agents)
- [Journeys for Android Studio](https://developer.android.com/studio/gemini/journeys)
- [Android CLI and Skills Blog Post](https://developer.android.com/blog/posts/android-cli-build-android-apps-3x-faster-using-any-agent)
- [Google Cloud Application Default Credentials](https://cloud.google.com/sdk/docs/install)

## Run attempt 1

I've read the doc and checked this machine. Here's the situation.

What it takes to run a journey test from the CLI

Per the doc (Part 2 → Execution, and the Gaps table), four things must be in place:

┌────────────────────────────────────────────────┬────────────────────────────────────────────────────────────────┐
│                  Requirement                   │                     Status on this machine                     │
├────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Emulator running     e                         │ ✅ emulator-5554  is up                                         │
├────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ testSuites block in build.gradle.kts           │ ✅ present (journeysTest suite, default target, debug variant)  │
├────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Journey file in app/src/journeysTest/journeys/ │ ✅  regular_season_quiz.journey.xml exists                      │
├────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Google Cloud auth for the Gemini API           │ ❌  missing — this is the blocker                               │
└────────────────────────────────────────────────┴────────────────────────────────────────────────────────────────┘

The blocker: authentication

Journey tests aren't ordinary Espresso tests — at runtime Gemini reads a screenshot and decides each action, so the Gradle task calls the Gemini API and needs Google Cloud
credentials (doc lines 257–269). On this machine:

- gcloud CLI — not installed
- ADC file ~/.config/gcloud/application_default_credentials.json — missing
- GOOGLE_APPLICATION_CREDENTIALS env var — unset

That's almost certainly why my run "succeeded" but reported 0 tests — with no credentials the journeys engine has nothing to drive, so it discovers/runs nothing rather than
throwing a hard auth error.

When you run the journey manually in Android Studio, it works because the IDE supplies Gemini auth through your signed-in Google developer account + "Gemini in Android Studio"

Journey tests aren't ordinary Espresso tests — at runtime Gemini reads a screenshot and decides each action, so the Gradle task calls the Gemini API and needs Google Cloud
credentials (doc lines 257–269). On this machine:

- gcloud CLI — not installed
- ADC file ~/.config/gcloud/application_default_credentials.json — missing
- GOOGLE_APPLICATION_CREDENTIALS env var — unset

That's almost certainly why my run "succeeded" but reported 0 tests — with no credentials the journeys engine has nothing to drive, so it discovers/runs nothing rather than
throwing a hard auth error.

When you run the journey manually in Android Studio, it works because the IDE supplies Gemini auth through your signed-in Google developer account + "Gemini in Android Studio"
(doc line 182). That's an IDE-internal path — the command-line Gradle task can't use it; it needs Application Default Credentials instead.

What I need from you


1. Install the gcloud CLI — https://cloud.google.com/sdk/docs/install
2. Run the login yourself — in this session you can type:
   ! gcloud auth application-default login
2. so the output lands in our conversation. Use the Google account that has Gemini in Android Studio access.

Once ADC exists, I can run the journey from the CLI and actually fix it if a step fails.
