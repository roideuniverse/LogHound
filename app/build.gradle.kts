import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-api"))
            implementation(project(":database"))
            implementation(project(":core-impl"))
            implementation(project(":plugin-dsl"))
            implementation(project(":plugins:ui:log-viewer"))
            implementation(project(":plugins:ui:uuid-grouping"))
            implementation(project(":plugins:ui:sessions"))
            implementation(project(":plugins:data:logcat"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlin.scriptingJvmHost)
            implementation(libs.kotlin.scriptingJvm)
            implementation(libs.kotlin.scriptingCommon)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.junit)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.roideuniverse.loghound.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LogHound"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.roideuniverse.loghound"
            }
        }
    }
}
