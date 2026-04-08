plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    kotlin("plugin.compose")
}

import java.util.Properties
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sdk35ProfileEnabled = providers
    .gradleProperty("sdk35Profile")
    .orNull
    ?.toBooleanStrictOrNull() == true

val effectiveCompileSdk = 35
val effectiveTargetSdk = 35
val effectiveWorkManagerVersion = if (sdk35ProfileEnabled) "2.10.0" else "2.9.1"
val effectiveHiltVersion = providers.gradleProperty("hiltVersion").orElse("2.58")
val effectiveEventsBaseUrl = providers.gradleProperty("eventsBaseUrl").orElse("https://api.putivnyk.local/")
val effectiveEventsBaseUrlDebug = providers.gradleProperty("eventsBaseUrlDebug").orElse(effectiveEventsBaseUrl)
val secureEventsBaseUrl = effectiveEventsBaseUrl.map { raw ->
    val normalized = raw.trim()
    require(normalized.endsWith("/")) { "eventsBaseUrl must end with '/'" }
    val parsed = runCatching { URI(normalized) }.getOrNull()
    require(parsed?.scheme.equals("https", ignoreCase = true) && !parsed?.host.isNullOrBlank()) {
        "eventsBaseUrl must be a valid HTTPS URL"
    }
    normalized
}
val secureEventsBaseUrlDebug = effectiveEventsBaseUrlDebug.map { raw ->
    val normalized = raw.trim()
    require(normalized.endsWith("/")) { "eventsBaseUrlDebug must end with '/'" }
    val parsed = runCatching { URI(normalized) }.getOrNull()
    require(parsed?.scheme.equals("https", ignoreCase = true) && !parsed?.host.isNullOrBlank()) {
        "eventsBaseUrlDebug must be a valid HTTPS URL"
    }
    normalized
}
val effectiveMapLibreStyleUri = providers.gradleProperty("mapLibreStyleUri").orElse("asset://maps/maplibre_style.json")
val secureMapLibreStyleUri = effectiveMapLibreStyleUri.map { raw ->
    val normalized = raw.trim()
    require(normalized.isNotBlank()) { "mapLibreStyleUri must not be blank" }
    val parsed = runCatching { URI(normalized) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase()
    require(scheme in setOf("asset", "https", "http", "file")) {
        "mapLibreStyleUri must use one of supported schemes: asset, https, http, file"
    }
    if (scheme == "asset") {
        require(!parsed?.schemeSpecificPart.isNullOrBlank()) {
            "asset mapLibreStyleUri must include asset path"
        }
    }
    if (scheme == "https" || scheme == "http") {
        require(!parsed?.host.isNullOrBlank()) {
            "network mapLibreStyleUri must include host"
        }
    }
    normalized
}
val effectiveMapLibreClusterRadius = providers.gradleProperty("mapLibreClusterRadius").orElse("48")
val effectiveMapLibreClusterMinPoints = providers.gradleProperty("mapLibreClusterMinPoints").orElse("2")
val effectiveMapLibreClusterTapPadding = providers.gradleProperty("mapLibreClusterTapPadding").orElse("96")
val effectiveMapLibreClusterTapDurationMs = providers.gradleProperty("mapLibreClusterTapDurationMs").orElse("350")
val debugMapLibreClusterRadius = providers.gradleProperty("mapLibreClusterRadiusDebug").orElse(effectiveMapLibreClusterRadius)
val debugMapLibreClusterMinPoints = providers.gradleProperty("mapLibreClusterMinPointsDebug").orElse(effectiveMapLibreClusterMinPoints)
val debugMapLibreClusterTapPadding = providers.gradleProperty("mapLibreClusterTapPaddingDebug").orElse(effectiveMapLibreClusterTapPadding)
val debugMapLibreClusterTapDurationMs = providers.gradleProperty("mapLibreClusterTapDurationMsDebug").orElse(effectiveMapLibreClusterTapDurationMs)
val releaseMapLibreClusterRadius = providers.gradleProperty("mapLibreClusterRadiusRelease").orElse(effectiveMapLibreClusterRadius)
val releaseMapLibreClusterMinPoints = providers.gradleProperty("mapLibreClusterMinPointsRelease").orElse(effectiveMapLibreClusterMinPoints)
val releaseMapLibreClusterTapPadding = providers.gradleProperty("mapLibreClusterTapPaddingRelease").orElse(effectiveMapLibreClusterTapPadding)
val releaseMapLibreClusterTapDurationMs = providers.gradleProperty("mapLibreClusterTapDurationMsRelease").orElse(effectiveMapLibreClusterTapDurationMs)

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "ua.kyiv.putivnyk"
    compileSdk = effectiveCompileSdk

    defaultConfig {
        applicationId = "ua.kyiv.putivnyk"
        minSdk = 29
        targetSdk = effectiveTargetSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "MAPLIBRE_STYLE_URI", "\"${secureMapLibreStyleUri.get()}\"")
        buildConfigField("int", "MAPLIBRE_CLUSTER_RADIUS", effectiveMapLibreClusterRadius.get())
        buildConfigField("int", "MAPLIBRE_CLUSTER_MIN_POINTS", effectiveMapLibreClusterMinPoints.get())
        buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_PADDING", effectiveMapLibreClusterTapPadding.get())
        buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_DURATION_MS", effectiveMapLibreClusterTapDurationMs.get())

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }

    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        val keystoreProperties = Properties().apply {
            val file = rootProject.file("keystore.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }

        if (keystoreProperties.isNotEmpty()) {
            signingConfigs {
                create("release") {
                    storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("String", "EVENTS_BASE_URL", "\"${secureEventsBaseUrl.get()}\"")
            buildConfigField("int", "MAPLIBRE_CLUSTER_RADIUS", releaseMapLibreClusterRadius.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_MIN_POINTS", releaseMapLibreClusterMinPoints.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_PADDING", releaseMapLibreClusterTapPadding.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_DURATION_MS", releaseMapLibreClusterTapDurationMs.get())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            buildConfigField("String", "EVENTS_BASE_URL", "\"${secureEventsBaseUrlDebug.get()}\"")
            buildConfigField("int", "MAPLIBRE_CLUSTER_RADIUS", debugMapLibreClusterRadius.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_MIN_POINTS", debugMapLibreClusterMinPoints.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_PADDING", debugMapLibreClusterTapPadding.get())
            buildConfigField("int", "MAPLIBRE_CLUSTER_TAP_DURATION_MS", debugMapLibreClusterTapDurationMs.get())
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.all {
            it.maxParallelForks = 1
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libtranslate_jni.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/versions/**"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.dagger:hilt-android:${effectiveHiltVersion.get()}")
    ksp("com.google.dagger:hilt-android-compiler:${effectiveHiltVersion.get()}")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:$effectiveWorkManagerVersion")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.mapsforge:mapsforge-core:0.19.0")
    implementation("org.mapsforge:mapsforge-map:0.19.0")
    implementation("org.mapsforge:mapsforge-map-android:0.19.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.19.0")
    implementation("org.mapsforge:mapsforge-themes:0.19.0")
    implementation("com.graphhopper:graphhopper-core:10.0") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "org.openstreetmap.osmosis")
        exclude(group = "org.apache.xmlgraphics")
        exclude(group = "ch.qos.logback")
    }
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation("org.maplibre.gl:android-sdk:12.3.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir(".cxx"))
    delete(layout.buildDirectory.dir("intermediates/cxx"))
}

val cleanConnectedAndroidTestArtifacts by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("outputs/androidTest-results/connected"))
    delete(layout.buildDirectory.dir("reports/androidTests/connected"))
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(cleanConnectedAndroidTestArtifacts)
    doNotTrackState("UTP creates transient lock files in connected test outputs that break Gradle state tracking")
}


