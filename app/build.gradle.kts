plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseStoreFile = providers.environmentVariable("NIANRI_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("NIANRI_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NIANRI_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NIANRI_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

check(!releaseTaskRequested || hasReleaseSigning) {
    "Release signing requires NIANRI_RELEASE_STORE_FILE, " +
        "NIANRI_RELEASE_STORE_PASSWORD, NIANRI_RELEASE_KEY_ALIAS, and " +
        "NIANRI_RELEASE_KEY_PASSWORD."
}

android {
    namespace = "com.nianri.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nianri.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        buildConfigField("String", "APP_NAME", "\"念日\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val serializationBom = platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1")
    implementation(composeBom)
    implementation(serializationBom)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    ksp("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
