import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    val xcframework = XCFramework("PutivnykShared")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm("android") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosX64Target = iosX64()
    val iosArm64Target = iosArm64()
    val iosSimulatorArm64Target = iosSimulatorArm64()

    listOf(
        iosX64Target,
        iosArm64Target,
        iosSimulatorArm64Target
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PutivnykShared"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.ktor:ktor-client-mock:2.3.12")
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.12")
        }

        iosX64Main.get().dependsOn(iosMain)
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
    }
}
