plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "si.jakobkreft.ontime"
    compileSdk = 37

    defaultConfig {
        applicationId = "si.jakobkreft.ontime"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "3.4"

        buildConfigField("boolean", "PLAY_STORE", "false")
    }

    buildTypes {
        // The F-Droid and GitHub build. F-Droid's recipe runs plain `assembleRelease`, so this
        // one keeps its name and output path.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Reproducible builds: keep build-machine state out of the APK.
            vcsInfo.include = false
        }
        // The Google Play build (`bundlePlay`). Play does not allow links to outside payment, so
        // this one asks for a rating where the release build links to Ko-fi.
        create("play") {
            initWith(getByName("release"))
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "DebugProbesKt.bin",
            "META-INF/**.version",
            "kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
