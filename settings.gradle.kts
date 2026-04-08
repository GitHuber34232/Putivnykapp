pluginManagement {
    val agpChannel = providers.gradleProperty("agpChannel").orElse("9").get()
    val agpVersion = if (agpChannel == "8") {
        providers.gradleProperty("agp8Version").orElse("8.7.3").get()
    } else {
        providers.gradleProperty("agp9Version").orElse("9.0.1").get()
    }
    val kotlinVersion = "2.3.20"
    val hiltVersion = if (agpChannel == "8") {
        providers.gradleProperty("hilt8Version").orElse("2.58").get()
    } else {
        providers.gradleProperty("hilt9Version").orElse("2.59.2").get()
    }
    val kspVersion = providers.gradleProperty("kspVersion").orElse("2.3.6").get()

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.android.application") version agpVersion
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("com.google.dagger.hilt.android") version hiltVersion
        id("com.google.devtools.ksp") version kspVersion
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Putivnyk"
include(":app")
