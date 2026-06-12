plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    android.set(true)
    version.set("1.8.0")
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
        exclude { it.file.path.contains("/cpp/3rdparty/") }
    }
}

detekt {
    toolVersion = "1.23.7"
    config.setFrom("$projectDir/detekt.yml")
    buildUponDefaultConfig = true
    parallel = true
    baseline = file("$projectDir/detekt-baseline.xml")
    source.setFrom(files("src/main/java", "src/main/kotlin"))
}

// Read app version from VERSION file at project root.
// Format: YYYY.MM.DD.HH.mm  (zero-padded, UTC)
// versionName = content of VERSION as-is
val versionFile = file("${rootProject.projectDir}/VERSION")
val appVersionName = versionFile.readText().trim()
val versionParts = appVersionName.split(".").map { it.toInt() }
require(versionParts.size == 5) { "VERSION must contain exactly 5 parts: YYYY.MM.DD.HH.mm, got: $appVersionName" }

// versionCode = Unix timestamp derived from the VERSION file contents (UTC)
// Uses SimpleDateFormat (always available in Gradle) instead of java.time
val appVersionCode = try {
    val sdf = java.text.SimpleDateFormat("yyyy.MM.dd.HH.mm", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val date = requireNotNull(sdf.parse(appVersionName)) { "Failed to parse VERSION: $appVersionName" }
    (date.time / 1000L).toInt()
} catch (e: Exception) {
    // Fallback: remove dots, concatenate as integer (e.g. 2026.06.12.06.00 → 202606120600)
    println("WARNING: Cannot parse VERSION as timestamp, using fallback versionCode")
    appVersionName.replace(".", "").toLong().toInt()
}

android {
    namespace = "io.github.dreamandroid.local"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.dreamandroid.local"
        minSdk = 28
//        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
    }

    bundle {
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
        language {
            enableSplit = false
        }
    }
    buildTypes {
        release {
            // Use custom keystore if provided via properties, otherwise default to debug
            val storeFileProp = project.findProperty("RELEASE_STORE_FILE") as String?
            val storePassProp = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            if (!storeFileProp.isNullOrBlank() && !storePassProp.isNullOrBlank()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(storeFileProp)
                    storePassword = storePassProp
                    keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
                    keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
                }
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
//            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    flavorDimensions += "version"
    productFlavors {
        create("basic") {
            dimension = "version"
            versionNameSuffix = ""
        }
        create("filter") {
            dimension = "version"
            versionNameSuffix = "_with_filter"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("dreamandroid_armv8a_$versionName.apk")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material3.xml)
    implementation(libs.coil.compose)
    implementation(libs.cropify)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
