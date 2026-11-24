import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Renders the Play Store screenshots (StoreAssetsTest).
    alias(libs.plugins.paparazzi)
}

// `-PstoreAssets` renders the Play Store screenshots (StoreAssetsTest, see play-store-assets/listing.md).
// Paparazzi 1.3.4 can't render against SDK 36 yet, so that run compiles against 35.
val storeAssets = providers.gradleProperty("storeAssets").isPresent
val sdk = if (storeAssets) 35 else 36

// Release signing credentials live outside version control: see keystore/keystore.properties
// (gitignored). The release build is signed only when that file is present.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.github.guidohu.clockshift"
    compileSdk = sdk

    defaultConfig {
        applicationId = "com.github.guidohu.clockshift"
        minSdk = 26
        targetSdk = sdk
        versionCode = 9
        versionName = "1.8"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        warningsAsErrors = true
        abortOnError = true
        lintConfig = file("lint.xml")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// The store screenshots only run when asked for; at SDK 36 Paparazzi would fail them.
tasks.withType<Test>().configureEach {
    if (!storeAssets) exclude("**/StoreAssetsTest*")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.billing)
    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Android's org.json is only a stub on the JVM; the real library lets the backup format be unit-tested.
    testImplementation(libs.org.json)
    // A real SQLite on the JVM, to run the Room migrations against the exported schemas.
    testImplementation(libs.sqlite.jdbc)
    // Stands in for system services layoutlib lacks when rendering the store screenshots.
    testImplementation(libs.mockk)
}
