plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.meta.spatial)
}

android {
    namespace = "com.example.pucktrivia"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pucktrivia"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Quest-only debug features (hot reload, cast input forwarding) are gated on
        // BuildConfig.DEBUG, which has to actually be generated.
        buildConfig = true
    }

    // Two shipping targets that share every line of game logic and every 2D screen:
    //
    //  - mobile: phones, tablets, and Android XR headsets. Keeps androidx.xr, whose spatial
    //    panels are a *Google* Android XR API — Horizon OS has no androidx.xr runtime, so on a
    //    Quest those composables would report no spatial capability at best and fail to resolve a
    //    session at worst.
    //  - quest:  Meta Horizon OS. Swaps androidx.xr for Meta Spatial SDK and replaces the
    //    launcher activity with an immersive one.
    //
    // Flavors rather than a single runtime-detected build (HorizonOsDetector.isOnHorizonOs) because
    // the two XR stacks are mutually exclusive *dependencies*, not just mutually exclusive code
    // paths — keeping both in one APK would ship two spatial runtimes to every device.
    flavorDimensions += "platform"
    productFlavors {
        create("mobile") { dimension = "platform" }
        create("quest") {
            dimension = "platform"
            // Distinct ID so both builds can sit on one device during development, and so the
            // Horizon Store listing is a separate app entry from the Play listing.
            applicationIdSuffix = ".quest"
            // Horizon OS v74+ (Quest 3 / 3S passthrough camera era) is the floor this spike
            // assumes; the AndroidManifest states the Horizon OS SDK range explicitly.
            minSdk = 32
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        suites {
            create("journeysTest") {
                targets {
                    create("default") {
                    }
                }
                useJunitEngine {
                    inputs += listOf(com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS)
                    includeEngines += listOf("journeys-test-engine")
                    enginesDependencies(libs.junit.platform.launcher)
                    enginesDependencies(libs.junit.platform.engine)
                    enginesDependencies(libs.journeys.junit.engine)
                }
                // Journeys drive a touch/pointer UI, which is the mobile flavor. The quest
                // flavor's entry point is an immersive activity with no 2D screen to walk.
                targetVariants += listOf("mobileDebug")
            }
        }
    }
}

// The Journeys JUnit engine ships classes compiled for Java 21 (class file v65),
// so the journeys test suite must run on a JDK 21+ launcher. Unit tests and the
// rest of the build are left on whatever JDK the contributor/IDE is using.
tasks.withType<Test>().configureEach {
    if (name.startsWith("testJourneys")) {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Android XR (Google) — mobile flavor only. See the productFlavors comment above.
    "mobileImplementation"(libs.androidx.xr.compose)
    "mobileImplementation"(libs.androidx.xr.scenecore)
    "mobileImplementation"(libs.androidx.xr.runtime)

    // Meta Horizon OS — quest flavor only.
    //   meta-spatial-sdk          core ECS + runtime, required by everything below
    //   meta-spatial-sdk-toolkit  PanelRegistration / Entity.createPanelEntity / AppSystemActivity
    //   meta-spatial-sdk-vr       VRFeature: head tracking, controllers, hands
    //   meta-spatial-sdk-compose  ComposeFeature + ComposeViewPanelRegistration
    //   meta-spatial-sdk-isdk     IsdkPanelResize — the grab/resize analogue of androidx.xr's
    //                             .movable() / .resizable(). Interaction SDK itself is already
    //                             active by way of VRFeature; this artifact is what puts its
    //                             components on the compile classpath.
    "questImplementation"(libs.meta.spatial.sdk)
    "questImplementation"(libs.meta.spatial.sdk.toolkit)
    "questImplementation"(libs.meta.spatial.sdk.vr)
    "questImplementation"(libs.meta.spatial.sdk.compose)
    "questImplementation"(libs.meta.spatial.sdk.isdk)
    // Lets a desktop mouse/keyboard drive the headset over cast, so panel layout can be iterated
    // on without donning the device. It opens a local socket, so the feature is registered only
    // under BuildConfig.DEBUG in PuckTriviaImmersiveActivity.registerFeatures.
    //
    // Ideally this would be "questDebugImplementation" so it is absent from release builds
    // entirely, but flavor+buildType configurations are created per-variant and are not resolvable
    // from this block on AGP 9. Stripping it from release is a follow-up.
    "questImplementation"(libs.meta.spatial.sdk.castinputforward)
    // HorizonOsDetector. Not strictly needed once flavors exist, but shared `main` code that wants
    // to branch without a flavor-specific source set still has a supported way to ask.
    "questImplementation"(libs.meta.horizonosx.core)
    // Spatial SDK's annotation processor, for @ComponentData-style codegen. No custom ECS
    // components exist yet; wired up now so adding one is a one-file change.
    "kspQuest"(libs.meta.spatial.sdk.toolkit)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}