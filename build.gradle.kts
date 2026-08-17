// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    // Meta Spatial SDK's Gradle plugin — declared here, applied in :app. It contributes the
    // Spatial Editor scene-export tasks and the KSP-backed component codegen the immersive
    // (quest-flavor) activity relies on.
    alias(libs.plugins.meta.spatial) apply false
}
